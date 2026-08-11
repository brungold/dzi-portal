/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.infrastructure.audit;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.dzi.portal.common.audit.AuditEntry;
import pl.dzi.portal.common.audit.AuditStatus;
import pl.dzi.portal.common.audit.AuditWriter;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test integracyjny na PRAWDZIWYM SQL Server (Testcontainers). Sprawdza trzy rzeczy,
 * których nie da się uczciwie przetestować inaczej:
 *  1) komplet migracji (schemat plik-per-tabela V1-V11 + widoki V12/V13) przechodzi na czystej bazie,
 *  2) AuditWriter zapisuje pełny wpis, a znacznik czasu pochodzi z Clocka,
 *  3) semantyka append-only z prod-grants.sql działa: DENY UPDATE/DELETE wygrywa
 *     z rolą db_datawriter (INSERT wolno, UPDATE/DELETE — nie).
 *
 * Celowo BEZ kontekstu Springa: czysty JUnit + Flyway API + JdbcTemplate — zero
 * zależności od plasterków Boota. Bez Dockera klasa sama się pomija.
 * Uruchamiana przez failsafe (faza verify), bo nazwa kończy się na *IT.
 */
@Testcontainers(disabledWithoutDocker = true)
class AuditPersistenceIT {

    @Container
    private static final MSSQLServerContainer<?> MSSQL =
            new MSSQLServerContainer<>(DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest"))
                    .acceptLicense();

    private static String databaseUrl;
    private static JdbcTemplate adminJdbc;

    @BeforeAll
    static void createDatabaseAndMigrate() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MSSQL.getJdbcUrl(), MSSQL.getUsername(), MSSQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE portal_it");
        }
        databaseUrl = MSSQL.getJdbcUrl() + ";databaseName=portal_it";

        Flyway.configure()
                .dataSource(databaseUrl, MSSQL.getUsername(), MSSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        adminJdbc = new JdbcTemplate(new DriverManagerDataSource(databaseUrl, MSSQL.getUsername(), MSSQL.getPassword()));
    }

    @Test
    void should_apply_all_migrations_including_audit_object_ref() {
        Integer migrations = adminJdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class);
        assertThat(migrations).isGreaterThanOrEqualTo(2);

        Integer auditColumns = adminJdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = 'audit_log' AND COLUMN_NAME IN ('action', 'object_ref')
                """, Integer.class);
        assertThat(auditColumns).isEqualTo(2);
    }

    @Test
    void should_persist_full_entry_with_timestamp_from_clock() {
        // given
        Instant fixedInstant = Instant.parse("2026-07-07T10:15:30Z");
        var writer = new AuditWriter(adminJdbc, Clock.fixed(fixedInstant, ZoneOffset.UTC));
        String correlationId = UUID.randomUUID().toString();

        // when
        writer.write(new AuditEntry("jkowalski", "10.0.5.7", "POST", "/api/tiles/42/run",
                "TILE_EXECUTE", "tile:42", AuditStatus.SUCCESS, 200, 12, correlationId));

        // then
        Map<String, Object> row = adminJdbc.queryForMap(
                "SELECT * FROM audit_log WHERE correlation_id = ?", correlationId);
        assertThat(row.get("username")).isEqualTo("jkowalski");
        assertThat(row.get("client_ip")).isEqualTo("10.0.5.7");
        assertThat(row.get("action")).isEqualTo("TILE_EXECUTE");
        assertThat(row.get("object_ref")).isEqualTo("tile:42");
        assertThat(row.get("status")).isEqualTo("SUCCESS");
        // Odczyt jako LocalDateTime — niezależnie od strefy JVM testu. Poprzednia asercja
        // (Timestamp.toInstant) przechodziła nawet z defektem strefy w AuditWriter, bo zapis
        // i odczyt przez Timestamp myliły się symetrycznie i błąd się znosił (2026-08-06).
        LocalDateTime storedTs = adminJdbc.queryForObject(
                "SELECT ts_utc FROM audit_log WHERE correlation_id = ?", LocalDateTime.class, correlationId);
        assertThat(storedTs).isEqualTo(LocalDateTime.ofInstant(fixedInstant, ZoneOffset.UTC));
    }

    @Test
    void should_expose_polish_local_time_via_view() {
        // Widok v_audit_log_pl (V5): ts_pl = ts_utc przeliczony na strefę Polski,
        // z automatyczną obsługą czasu letniego/zimowego przez AT TIME ZONE.
        var summer = Instant.parse("2026-07-01T10:00:00Z");   // CEST: UTC+2
        var winter = Instant.parse("2026-01-15T10:00:00Z");   // CET:  UTC+1
        String summerCid = UUID.randomUUID().toString();
        String winterCid = UUID.randomUUID().toString();
        new AuditWriter(adminJdbc, Clock.fixed(summer, ZoneOffset.UTC)).write(entryWith(summerCid));
        new AuditWriter(adminJdbc, Clock.fixed(winter, ZoneOffset.UTC)).write(entryWith(winterCid));

        assertThat(tsPlFor(summerCid)).isEqualTo(LocalDateTime.of(2026, 7, 1, 12, 0));
        assertThat(tsPlFor(winterCid)).isEqualTo(LocalDateTime.of(2026, 1, 15, 11, 0));
    }

    private static AuditEntry entryWith(String correlationId) {
        return new AuditEntry("jkowalski", "10.0.5.7", "GET", "/api/whoami",
                null, null, AuditStatus.SUCCESS, 200, 5, correlationId);
    }

    private static LocalDateTime tsPlFor(String correlationId) {
        return adminJdbc.queryForObject(
                "SELECT ts_pl FROM v_audit_log_pl WHERE correlation_id = ?", LocalDateTime.class, correlationId);
    }

    @Test
    void should_enforce_append_only_for_runtime_account() {
        // given: konto o uprawnieniach identycznych jak gMSA w prod (prod-grants.sql)
        adminJdbc.execute("CREATE LOGIN portal_runtime WITH PASSWORD = 'ItOnly!23456789', CHECK_POLICY = OFF");
        adminJdbc.execute("CREATE USER portal_runtime FOR LOGIN portal_runtime");
        adminJdbc.execute("ALTER ROLE db_datareader ADD MEMBER portal_runtime");
        adminJdbc.execute("ALTER ROLE db_datawriter ADD MEMBER portal_runtime");
        adminJdbc.execute("DENY UPDATE, DELETE ON dbo.audit_log TO portal_runtime");

        var runtimeJdbc = new JdbcTemplate(
                new DriverManagerDataSource(databaseUrl, "portal_runtime", "ItOnly!23456789"));

        // when / then: INSERT wolno (db_datawriter)...
        runtimeJdbc.update("""
                INSERT INTO audit_log (ts_utc, username, client_ip, http_method, path,
                                       action, object_ref, status, http_status, duration_ms, correlation_id)
                VALUES (SYSUTCDATETIME(), 'runtime', '127.0.0.1', 'GET', '/api/x',
                        NULL, NULL, 'SUCCESS', 200, 1, ?)
                """, UUID.randomUUID().toString());

        // ...ale UPDATE i DELETE blokuje DENY — audyt jest append-only
        assertThatThrownBy(() -> runtimeJdbc.update("UPDATE audit_log SET username = 'hacked'"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> runtimeJdbc.update("DELETE FROM audit_log"))
                .isInstanceOf(DataAccessException.class);
    }
}
