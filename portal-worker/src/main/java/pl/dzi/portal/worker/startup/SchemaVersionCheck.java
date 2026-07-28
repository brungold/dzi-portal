/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.worker.startup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Migracje uruchamia wyłącznie portal-api (jeden właściciel schematu — ADR-0001).
 * Worker przed startem sprawdza tylko, czy schemat istnieje: tani bezpiecznik
 * przed cichym startem na pustej lub niezmigrowanej bazie.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SchemaVersionCheck implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer applied = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class);
            if (applied == null || applied == 0) {
                throw new IllegalStateException(
                        "Baza istnieje, ale nie ma zastosowanych migracji. Uruchom najpierw portal-api.");
            }
            log.info("Schemat bazy OK — zastosowanych migracji: {}", applied);
        } catch (DataAccessException e) {
            throw new IllegalStateException(
                    "Brak schematu bazy (nie znaleziono flyway_schema_history). "
                            + "Uruchom najpierw portal-api — to on wykonuje migracje Flyway.", e);
        }
    }
}
