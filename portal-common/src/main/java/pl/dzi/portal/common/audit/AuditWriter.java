/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.common.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;

/**
 * Świadomie JdbcTemplate zamiast repozytorium Spring Data: tabela jest append-only
 * (w prod DENY UPDATE/DELETE — patrz deploy/sql/prod-grants.sql), a aplikacja nie ma
 * modelu odczytu audytu. Jeden INSERT, zero abstrakcji.
 *
 * Celowo klasa, nie interfejs — jest dokładnie jedna implementacja; testy nadpisują
 * write() przez dziedziczenie (patrz AuditFilterTest), a append-only weryfikuje
 * test integracyjny na prawdziwym SQL Server (AuditPersistenceIT).
 */
@Component
@Profile("!demo")   // zachowane celowo: w tym wariancie (bez klas demo) aktywacja profilu demo = błąd startu (ADR-0004)
@RequiredArgsConstructor
public class AuditWriter {

    private static final String INSERT_SQL = """
            INSERT INTO audit_log
                (ts_utc, username, client_ip, http_method, path, action, object_ref,
                 status, http_status, duration_ms, correlation_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public void write(AuditEntry entry) {
        jdbcTemplate.update(INSERT_SQL,
                Timestamp.from(clock.instant()),
                entry.username(),
                entry.clientIp(),
                entry.httpMethod(),
                entry.path(),
                entry.action(),
                entry.objectRef(),
                entry.status().name(),
                entry.httpStatus(),
                entry.durationMs(),
                entry.correlationId());
    }
}
