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
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.dzi.portal.common.task.TaskStatus;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Operacje kolejkowe na tabeli tasks — jawny T-SQL, bo to jedyne miejsce w systemie,
 * gdzie liczą się hinty blokad SQL Servera:
 *
 *  - READPAST: pomiń wiersze zablokowane przez innego workera (zamiast na nich czekać),
 *  - UPDLOCK + ROWLOCK: zarezerwuj wiersz na czas UPDATE — dwóch workerów NIGDY
 *    nie dostanie tego samego zadania,
 *  - OUTPUT inserted...: claim i odczyt w JEDNYM zapytaniu, bez okna wyścigu.
 *
 * To kanoniczny wzorzec kolejki na SQL Server; atomowość dowodzi TaskQueueIT.
 */
@Slf4j
@RequiredArgsConstructor
public class TaskQueue {

    private static final String CLAIM_SQL = """
            WITH next_task AS (
                SELECT TOP (1) id, script_id, correlation_id, status, started_at, worker_host, version
                FROM tasks WITH (ROWLOCK, UPDLOCK, READPAST)
                WHERE status = 'PENDING'
                ORDER BY id
            )
            UPDATE next_task
            SET status = 'IN_PROGRESS', started_at = ?, worker_host = ?, version = version + 1
            OUTPUT inserted.id, inserted.script_id, inserted.correlation_id
            """;

    /** Guard na status: jeśli sweeper zdążył oznaczyć TIMED_OUT, nie nadpisujemy wyniku. */
    private static final String FINISH_SQL = """
            UPDATE tasks
            SET status = ?, finished_at = ?, exit_code = ?, version = version + 1
            WHERE id = ? AND status = 'IN_PROGRESS'
            """;

    /**
     * Zadania osierocone: IN_PROGRESS dłużej niż timeout skryptu + 60 s marginesu.
     * Jedyny realny scenariusz: worker padł/został zrestartowany w trakcie egzekucji.
     */
    private static final String SWEEP_SQL = """
            UPDATE t
            SET status = 'TIMED_OUT', finished_at = ?, version = t.version + 1
            OUTPUT inserted.id
            FROM tasks t
            JOIN scripts s ON s.id = t.script_id
            WHERE t.status = 'IN_PROGRESS'
              AND t.started_at < DATEADD(SECOND, -(s.timeout_seconds + 60), ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public Optional<ClaimedTask> claimNext(String workerHost) {
        List<ClaimedTask> claimed = jdbcTemplate.query(CLAIM_SQL,
                (rs, rowNum) -> new ClaimedTask(rs.getLong("id"), rs.getLong("script_id"),
                        rs.getString("correlation_id")),
                Timestamp.from(clock.instant()), workerHost);
        return claimed.stream().findFirst();
    }

    public void markFinished(long taskId, TaskStatus status, Integer exitCode) {
        int updated = jdbcTemplate.update(FINISH_SQL,
                status.name(), Timestamp.from(clock.instant()), exitCode, taskId);
        if (updated == 0) {
            log.warn("Zadanie {} nie było już IN_PROGRESS przy zamykaniu (sweeper oznaczył TIMED_OUT?)", taskId);
        }
    }

    public List<Long> sweepOrphans() {
        Timestamp now = Timestamp.from(clock.instant());
        return jdbcTemplate.query(SWEEP_SQL, (rs, rowNum) -> rs.getLong("id"), now, now);
    }
}
