/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.common.task;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Linia wyjścia zadania (stdout/stderr skryptu albo SYSTEM od workera).
 * W common, bo dotykają jej OBA procesy: worker pisze (JdbcTemplate, strumieniowo),
 * api czyta (to repozytorium) do podglądu w UI.
 */
@Table("task_log")
public record TaskLogEntry(
        @Id Long id,
        Long taskId,
        Instant tsUtc,
        String stream,
        String line) {
}
