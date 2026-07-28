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

import org.junit.jupiter.api.Test;
import pl.dzi.portal.common.script.Script;
import pl.dzi.portal.common.script.ScriptRepository;
import pl.dzi.portal.common.script.ScriptType;
import pl.dzi.portal.common.task.TaskStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Orkiestracja na double'ach: kolejka, runner i log w pamięci (wzorzec z JobOffers).
 * Prawdziwe procesy i prawdziwy SQL mają własne testy (demo-skrypty + TaskQueueIT).
 */
class TaskExecutorTest {

    private final StubTaskQueue queue = new StubTaskQueue();
    private final CapturingTaskLogWriter log = new CapturingTaskLogWriter();
    private final StubProcessRunner runner = new StubProcessRunner(log);
    private final Map<Long, Script> scriptsById = new HashMap<>();
    private final ScriptRepository scripts = new ScriptRepository() {
        @Override public Optional<Script> findById(Long id) { return Optional.ofNullable(scriptsById.get(id)); }
        @Override public Optional<Script> findByCode(String code) { return Optional.empty(); }
    };
    private final TaskExecutor executor = new TaskExecutor(queue, scripts, runner, log, "TEST-HOST");

    @Test
    void should_mark_succeeded_for_exit_code_zero() throws IOException {
        givenClaimedTaskWithExistingScript(1L, 10L);
        runner.result = new ProcessRunner.RunResult(0, false);

        executor.drainQueue();

        assertThat(queue.finished).containsEntry(1L, TaskStatus.SUCCEEDED);
        assertThat(queue.exitCodes).containsEntry(1L, 0);
    }

    @Test
    void should_mark_failed_with_exit_code_for_nonzero() throws IOException {
        givenClaimedTaskWithExistingScript(2L, 10L);
        runner.result = new ProcessRunner.RunResult(3, false);

        executor.drainQueue();

        assertThat(queue.finished).containsEntry(2L, TaskStatus.FAILED);
        assertThat(queue.exitCodes).containsEntry(2L, 3);
    }

    @Test
    void should_mark_timed_out_without_exit_code() throws IOException {
        givenClaimedTaskWithExistingScript(3L, 10L);
        runner.result = new ProcessRunner.RunResult(-1, true);

        executor.drainQueue();

        assertThat(queue.finished).containsEntry(3L, TaskStatus.TIMED_OUT);
        assertThat(queue.exitCodes.get(3L)).isNull();
    }

    @Test
    void should_fail_task_when_script_missing_from_whitelist() {
        queue.pending.add(new ClaimedTask(4L, 999L, "cid-4")); // brak skryptu 999

        executor.drainQueue();

        assertThat(queue.finished).containsEntry(4L, TaskStatus.FAILED);
        assertThat(log.lines).anyMatch(line -> line.contains("Skrypt nie istnieje"));
    }

    @Test
    void should_fail_task_when_script_file_absent_on_disk() {
        queue.pending.add(new ClaimedTask(5L, 20L, "cid-5"));
        scriptsById.put(20L, new Script(20L, "ghost", "Z:\\nie\\ma\\takiego.ps1", ScriptType.PS1, null, 60, true));

        executor.drainQueue();

        assertThat(queue.finished).containsEntry(5L, TaskStatus.FAILED);
        assertThat(log.lines).anyMatch(line -> line.contains("Plik skryptu nie istnieje"));
    }

    @Test
    void should_fail_task_and_survive_runner_exception() throws IOException {
        givenClaimedTaskWithExistingScript(6L, 10L);
        runner.throwOnRun = new IOException("CreateProcess error=2");

        executor.drainQueue();

        assertThat(queue.finished).containsEntry(6L, TaskStatus.FAILED);
        assertThat(log.lines).anyMatch(line -> line.contains("Awaria workera"));
    }

    private void givenClaimedTaskWithExistingScript(long taskId, long scriptId) throws IOException {
        Path realFile = Files.createTempFile("portal-test", ".ps1");
        Files.writeString(realFile, "exit 0", StandardCharsets.UTF_8);
        realFile.toFile().deleteOnExit();
        scriptsById.put(scriptId, new Script(scriptId, "s" + scriptId, realFile.toString(),
                ScriptType.PS1, null, 60, true));
        queue.pending.add(new ClaimedTask(taskId, scriptId, "cid-" + taskId));
    }

    /** Double kolejki: konstruktor bazowy dostaje nulle — nadpisujemy wszystko, czego używa executor. */
    private static final class StubTaskQueue extends TaskQueue {
        private final List<ClaimedTask> pending = new ArrayList<>();
        private final Map<Long, TaskStatus> finished = new HashMap<>();
        private final Map<Long, Integer> exitCodes = new HashMap<>();

        private StubTaskQueue() { super(null, null); }

        @Override public Optional<ClaimedTask> claimNext(String workerHost) {
            return pending.isEmpty() ? Optional.empty() : Optional.of(pending.remove(0));
        }

        @Override public void markFinished(long taskId, TaskStatus status, Integer exitCode) {
            finished.put(taskId, status);
            exitCodes.put(taskId, exitCode);
        }
    }

    private static final class CapturingTaskLogWriter extends TaskLogWriter {
        private final List<String> lines = new ArrayList<>();
        private CapturingTaskLogWriter() { super(null); }
        @Override public void append(long taskId, String stream, String line) { lines.add(stream + ": " + line); }
    }

    private static final class StubProcessRunner extends ProcessRunner {
        private RunResult result = new RunResult(0, false);
        private IOException throwOnRun;
        private StubProcessRunner(TaskLogWriter log) { super(log, StandardCharsets.UTF_8); }
        @Override public RunResult run(Script script, long taskId, String correlationId) throws IOException {
            if (throwOnRun != null) { throw throwOnRun; }
            return result;
        }
    }
}
