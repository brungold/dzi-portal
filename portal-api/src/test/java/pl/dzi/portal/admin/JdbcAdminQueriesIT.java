/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.admin;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.dzi.portal.admin.AdminQueries.AuditRow;
import pl.dzi.portal.admin.AdminQueries.KnownLogin;
import pl.dzi.portal.admin.AdminQueries.PermissionRow;
import pl.dzi.portal.admin.AdminQueries.TileRow;
import pl.dzi.portal.admin.AdminQueries.TileStat;
import pl.dzi.portal.admin.AdminQueries.TileVisits;
import pl.dzi.portal.admin.AdminQueries.UserStat;
import pl.dzi.portal.admin.AdminQueries.Visitor;
import pl.dzi.portal.common.audit.AuditEntry;
import pl.dzi.portal.common.audit.AuditStatus;
import pl.dzi.portal.common.audit.AuditWriter;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-SQL modelu odczytu panelu na prawdziwym SQL Serverze 2022 (Testcontainers), po pełnych
 * migracjach Flyway. Wpisy audytu wstawia produkcyjny AuditWriter z zegarem stałym —
 * czyli dokładnie tak, jak powstają na serwerze. Bez Dockera test jest pomijany.
 */
@Testcontainers(disabledWithoutDocker = true)
class JdbcAdminQueriesIT {

    @Container
    private static final MSSQLServerContainer<?> MSSQL =
            new MSSQLServerContainer<>(DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest"))
                    .acceptLicense();

    private static JdbcTemplate jdbc;
    private static JdbcAdminQueries queries;

    private static final Instant T1 = Instant.parse("2026-09-01T08:00:00Z");
    private static final Instant T2 = Instant.parse("2026-09-05T09:00:00Z");
    private static final Instant T3 = Instant.parse("2026-09-08T10:00:00Z");
    private static final Instant OLD = Instant.parse("2026-06-01T10:00:00Z");

    @BeforeAll
    static void migrateAndSeed() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MSSQL.getJdbcUrl(), MSSQL.getUsername(), MSSQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE portal_admin_it");
        }
        String url = MSSQL.getJdbcUrl() + ";databaseName=portal_admin_it";
        Flyway.configure()
                .dataSource(url, MSSQL.getUsername(), MSSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(url, MSSQL.getUsername(), MSSQL.getPassword()));
        queries = new JdbcAdminQueries(jdbc);

        jdbc.update("INSERT INTO tiles (code, name, tile_type, action_ref, active, display_order) VALUES (?, ?, 'LINK', ?, 1, 10)",
                "red-pisma-sprawy", "ReD", "/apps/red-pisma-sprawy/");
        jdbc.update("INSERT INTO tiles (code, name, tile_type, action_ref, active, display_order) VALUES (?, ?, 'LINK', ?, 0, 40)",
                "wylaczony", "Nieaktywny", "/apps/wylaczony/");
        jdbc.update("INSERT INTO tiles (code, name, tile_type, action_ref, active, display_order) VALUES (?, ?, 'LINK', ?, 1, 900)",
                "administracja", "Administracja", "/apps/administracja/");
        jdbc.update("INSERT INTO tile_permissions (tile_id, ad_group, permission_level) SELECT id, ?, 'READ' FROM tiles WHERE code = ?",
                "jan.kowalski", "red-pisma-sprawy");
        jdbc.update("INSERT INTO tile_permissions (tile_id, ad_group, permission_level) SELECT id, ?, 'READ' FROM tiles WHERE code = ?",
                "wszyscy", "red-pisma-sprawy");
        jdbc.update("INSERT INTO tile_permissions (tile_id, ad_group, permission_level) SELECT id, ?, 'READ' FROM tiles WHERE code = ?",
                "admin.login", "administracja");

        write(T1, "jan.kowalski", "TILES_LIST", null, "/api/tiles", 200);
        write(T1.plusSeconds(5), "jan.kowalski", "APP_OPEN", "tile:red-pisma-sprawy", "/apps/red-pisma-sprawy/", 200);
        write(T2, "kowalska.anna2", "APP_OPEN", "tile:red-pisma-sprawy", "/apps/red-pisma-sprawy/", 200);
        write(T2.plusSeconds(1), "kowalska.anna2", "APP_DATA", "tile:red-pisma-sprawy", "/apps/red-pisma-sprawy/data/x.csv", 200);
        write(T3, "jan.kowalski", "APP_OPEN", "tile:red-pisma-sprawy", "/apps/red-pisma-sprawy/", 200);
        write(T3.plusSeconds(60), "jan.kowalski", "APP_DENIED", "tile:wylaczony", "/apps/wylaczony/", 403);
        write(OLD, "stary.login", "APP_OPEN", "tile:wylaczony", "/apps/wylaczony/", 200);
        write(T3.plusSeconds(120), "-", null, null, "/api/whoami", 401);
    }

    private static void write(Instant at, String user, String action, String objectRef, String path, int status) {
        AuditStatus auditStatus = status >= 400 ? AuditStatus.DENIED : AuditStatus.SUCCESS;
        new AuditWriter(jdbc, Clock.fixed(at, ZoneOffset.UTC)).write(new AuditEntry(
                user, "10.5.1.1", "GET", path, action, objectRef, auditStatus, status, 7, UUID.randomUUID().toString()));
    }

    @Test
    void should_list_tiles_in_home_page_order_and_all_permissions_with_codes() {
        List<TileRow> tiles = queries.tiles();
        assertThat(tiles).extracting(TileRow::code).containsExactly("red-pisma-sprawy", "wylaczony", "administracja");
        assertThat(tiles.get(1).active()).isFalse();

        List<PermissionRow> permissions = queries.permissions();
        assertThat(permissions).extracting(PermissionRow::tileCode)
                .containsExactly("red-pisma-sprawy", "red-pisma-sprawy", "administracja");
        assertThat(permissions.get(0).level()).isEqualTo("READ");
    }

    @Test
    void should_count_visits_per_tile_in_windows_and_remember_last_visit_outside_window() {
        Instant now = Instant.parse("2026-09-09T12:00:00Z");
        Map<String, TileVisits> visits = queries.visitsPerTile(now.minusSeconds(30L * 86400), now.minusSeconds(90L * 86400));

        assertThat(visits.get("red-pisma-sprawy").visits30()).isEqualTo(3);
        assertThat(visits.get("red-pisma-sprawy").visits90()).isEqualTo(3);
        assertThat(visits.get("red-pisma-sprawy").lastVisit()).isEqualTo(T3);
        assertThat(visits.get("wylaczony").visits30()).isZero();
        assertThat(visits.get("wylaczony").lastVisit()).isEqualTo(OLD);
        assertThat(visits).doesNotContainKey("administracja");
    }

    @Test
    void should_list_visitors_newest_first_and_only_openings() {
        List<Visitor> visitors = queries.visitorsOfTile("red-pisma-sprawy");
        assertThat(visitors).extracting(Visitor::login).containsExactly("jan.kowalski", "kowalska.anna2");
        assertThat(visitors.get(0).visits()).isEqualTo(2);      // APP_DATA anny nie jest wejściem
        assertThat(visitors.get(1).visits()).isEqualTo(1);
    }

    @Test
    void should_find_known_logins_with_fragment_filter_and_skip_anonymous() {
        assertThat(queries.knownLogins("")).extracting(KnownLogin::login)
                .containsExactly("jan.kowalski", "kowalska.anna2", "stary.login");
        assertThat(queries.knownLogins("KOWAL")).extracting(KnownLogin::login)
                .containsExactly("jan.kowalski", "kowalska.anna2");
        assertThat(queries.knownLogins("%")).isEmpty();          // znak specjalny LIKE jest szukany dosłownie
        assertThat(queries.presence("jan.kowalski")).isPresent();
        assertThat(queries.presence("jan.kowalski").get().firstSeen()).isEqualTo(T1);
        assertThat(queries.presence("nikt.taki")).isEmpty();
    }

    @Test
    void should_aggregate_stats_in_half_open_range() {
        Instant from = Instant.parse("2026-09-01T00:00:00Z");
        Instant to = Instant.parse("2026-09-08T10:00:00Z");     // koniec wyłączny: wejście o T3 nie wchodzi

        List<TileStat> tileStats = queries.tileStats(from, to);
        assertThat(tileStats).singleElement().satisfies(stat -> {
            assertThat(stat.tileCode()).isEqualTo("red-pisma-sprawy");
            assertThat(stat.visits()).isEqualTo(2);
            assertThat(stat.distinctUsers()).isEqualTo(2);
        });
        List<UserStat> userStats = queries.userStats(from, to);
        assertThat(userStats).extracting(UserStat::login).containsExactlyInAnyOrder("jan.kowalski", "kowalska.anna2");
        assertThat(queries.portalStats(from, to).visits()).isEqualTo(1);
        assertThat(queries.visitsOfUser("jan.kowalski")).singleElement()
                .satisfies(v -> assertThat(v.visits()).isEqualTo(2));
    }

    @Test
    void should_return_recent_audit_newest_first_with_optional_filters_and_clamped_limit() {
        List<AuditRow> all = queries.recentAudit(500, null, null, null);
        assertThat(all).hasSize(8);
        assertThat(all.get(0).path()).isEqualTo("/api/whoami");
        assertThat(all.get(0).username()).isEqualTo("-");

        assertThat(queries.recentAudit(1000, null, "APP_DENIED", null)).singleElement()
                .satisfies(row -> {
                    assertThat(row.httpStatus()).isEqualTo(403);
                    assertThat(row.status()).isEqualTo("DENIED");
                    assertThat(row.ts()).isEqualTo(T3.plusSeconds(60));
                });
        assertThat(queries.recentAudit(10, "jan.kowalski", "APP_OPEN", "red-pisma-sprawy")).hasSize(2);
        assertThat(queries.recentAudit(0, null, null, null)).hasSize(1);
    }
}
