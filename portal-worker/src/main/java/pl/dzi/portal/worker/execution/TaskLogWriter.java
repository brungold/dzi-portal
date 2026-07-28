/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.worker.execution;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Strumieniowy zapis linii wyjścia zadania. ts_utc nadaje DEFAULT bazy
 * (SYSUTCDATETIME) — dla logu kolejność po id wystarcza, a oszczędzamy
 * parametr na każdej z setek linii.
 */
@RequiredArgsConstructor
public class TaskLogWriter {

    public static final String STDOUT = "STDOUT";
    public static final String STDERR = "STDERR";
    public static final String SYSTEM = "SYSTEM";

    private static final int MAX_LINE_LENGTH = 4000; // NVARCHAR(4000) w task_log

    private final JdbcTemplate jdbcTemplate;

    public void append(long taskId, String stream, String line) {
        String safeLine = line == null ? "" : line;
        if (safeLine.length() > MAX_LINE_LENGTH) {
            safeLine = safeLine.substring(0, MAX_LINE_LENGTH);
        }
        jdbcTemplate.update("INSERT INTO task_log (task_id, stream, line) VALUES (?, ?, ?)",
                taskId, stream, safeLine);
    }
}
