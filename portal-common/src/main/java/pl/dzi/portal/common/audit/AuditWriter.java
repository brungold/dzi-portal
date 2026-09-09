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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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
        // ts_utc: jawnie "ścianka zegara" UTC (kontrakt kolumny i konwencja schematu z V1).
        // NIE java.sql.Timestamp: sterownik binduje Timestamp do strefowo-naiwnego DATETIME2
        // według strefy JVM, więc do kolumny trafiał czas LOKALNY (defekt wykryty 2026-08-06:
        // audyt 11:09:28 vs log IIS 09:09:28Z). LocalDateTime idzie do bazy 1:1, bez strefy.
        jdbcTemplate.update(INSERT_SQL,
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
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
