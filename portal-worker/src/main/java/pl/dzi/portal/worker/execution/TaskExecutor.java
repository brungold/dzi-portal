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
import org.slf4j.MDC;
import pl.dzi.portal.common.script.Script;
import pl.dzi.portal.common.script.ScriptRepository;
import pl.dzi.portal.common.task.TaskStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Orkiestracja: claim -> whitelist -> egzekucja -> status. Zadania wykonywane
 * SZEREGOWO (jedno naraz) — świadomie: skrypty operacyjne rzadko chcą działać
 * równolegle na tym samym serwerze; pula wątków to osobna decyzja, gdy zajdzie potrzeba.
 *
 * MDC dostaje correlation_id zadania, więc linie logu workera kleją się
 * z wpisem audytu HTTP, który to zadanie zlecił.
 */
@Slf4j
@RequiredArgsConstructor
public class TaskExecutor {

    private final TaskQueue taskQueue;
    private final ScriptRepository scripts;
    private final ProcessRunner processRunner;
    private final TaskLogWriter taskLog;
    private final String workerHost;

    /** Opróżnia kolejkę do dna; wraca, gdy nie ma nic do roboty. */
    public void drainQueue() {
        while (true) {
            Optional<ClaimedTask> claimed = taskQueue.claimNext(workerHost);
            if (claimed.isEmpty()) {
                return;
            }
            execute(claimed.get());
        }
    }

    private void execute(ClaimedTask task) {
        MDC.put("correlationId", task.correlationId());
        try {
            log.info("Wykonuję zadanie {} (skrypt id={})", task.id(), task.scriptId());
            Optional<Script> script = scripts.findById(task.scriptId()).filter(Script::active);
            if (script.isEmpty()) {
                fail(task.id(), "Skrypt nie istnieje albo został dezaktywowany po zleceniu zadania");
                return;
            }
            if (!Files.isRegularFile(Path.of(script.get().path()))) {
                fail(task.id(), "Plik skryptu nie istnieje: " + script.get().path()
                        + " (rozjazd whitelisty z dyskiem)");
                return;
            }

            ProcessRunner.RunResult result = processRunner.run(script.get(), task.id(), task.correlationId());
            if (result.timedOut()) {
                taskQueue.markFinished(task.id(), TaskStatus.TIMED_OUT, null);
                log.warn("Zadanie {} przekroczyło limit czasu", task.id());
            } else if (result.exitCode() == 0) {
                taskQueue.markFinished(task.id(), TaskStatus.SUCCEEDED, 0);
                taskLog.append(task.id(), TaskLogWriter.SYSTEM, "Zakończono poprawnie (kod 0)");
            } else {
                taskQueue.markFinished(task.id(), TaskStatus.FAILED, result.exitCode());
                taskLog.append(task.id(), TaskLogWriter.SYSTEM,
                        "Zakończono błędem (kod " + result.exitCode() + ")");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(task.id(), "Przerwano wykonywanie (zamykanie workera?)");
        } catch (Exception e) {
            log.error("Awaria wykonania zadania {}", task.id(), e);
            fail(task.id(), "Awaria workera: " + e.getMessage());
        } finally {
            MDC.remove("correlationId");
        }
    }

    private void fail(long taskId, String systemMessage) {
        taskLog.append(taskId, TaskLogWriter.SYSTEM, systemMessage);
        taskQueue.markFinished(taskId, TaskStatus.FAILED, null);
    }
}
