/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.worker.polling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.dzi.portal.worker.execution.TaskLogWriter;
import pl.dzi.portal.worker.execution.TaskQueue;

import java.util.List;

/**
 * Sprzątacz zadań osieroconych (worker padł w trakcie egzekucji): IN_PROGRESS
 * starsze niż timeout skryptu + 60 s margines -> TIMED_OUT + linia SYSTEM.
 * initialDelay=0: pierwszy przebieg od razu po starcie — typowy moment,
 * w którym sieroty w ogóle powstają, to właśnie restart.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OrphanSweeper {

    private final TaskQueue taskQueue;
    private final TaskLogWriter taskLog;

    @Scheduled(initialDelay = 0, fixedDelayString = "${portal.worker.sweep-delay:60s}")
    void sweep() {
        List<Long> orphaned = taskQueue.sweepOrphans();
        for (Long taskId : orphaned) {
            taskLog.append(taskId, TaskLogWriter.SYSTEM,
                    "Zadanie osierocone (worker przerwany w trakcie?) — oznaczono TIMED_OUT");
            log.warn("Osierocone zadanie {} oznaczone TIMED_OUT", taskId);
        }
    }
}
