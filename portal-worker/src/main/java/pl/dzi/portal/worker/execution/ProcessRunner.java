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
import pl.dzi.portal.common.script.Script;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Uruchamia proces skryptu z twardym timeoutem i strumieniuje stdout/stderr do task_log.
 *
 * Realia Windows, które ten kod obsługuje jawnie:
 *  - KODOWANIE: konsola PowerShell 5.1/cmd na polskim Windows mówi w OEM 852, nie UTF-8 —
 *    charset jest konfigurowalny (portal.worker.console-charset), domyślnie Cp852;
 *    skrypty pisane pod UTF-8 (chcp 65001) wymagają zmiany właściwości,
 *  - DRZEWO PROCESÓW: destroy() na powershell.exe NIE zabija procesów potomnych
 *    (np. odpalonego przez skrypt exe) — najpierw ubijamy descendants(), potem rodzica,
 *  - zmienne środowiskowe PORTAL_TASK_ID / PORTAL_CORRELATION_ID pozwalają skryptowi
 *    logować się spójnie z audytem portalu.
 */
@Slf4j
@RequiredArgsConstructor
public class ProcessRunner {

    /** Wynik egzekucji: exitCode = -1 gdy timedOut (proces ubity, kodu nie ma). */
    public record RunResult(int exitCode, boolean timedOut) {
    }

    private final TaskLogWriter taskLog;
    private final Charset consoleCharset;

    public RunResult run(Script script, long taskId, String correlationId) throws IOException, InterruptedException {
        Path scriptPath = Path.of(script.path());
        var command = ScriptCommands.commandFor(script);
        taskLog.append(taskId, TaskLogWriter.SYSTEM, "Uruchamiam: " + String.join(" ", command));

        var processBuilder = new ProcessBuilder(command);
        Path parent = scriptPath.getParent();
        if (parent != null && Files.isDirectory(parent)) {
            processBuilder.directory(parent.toFile());
        }
        processBuilder.environment().put("PORTAL_TASK_ID", String.valueOf(taskId));
        processBuilder.environment().put("PORTAL_CORRELATION_ID", correlationId);

        Process process = processBuilder.start();
        Thread stdout = streamToLog(process.getInputStream(), taskId, TaskLogWriter.STDOUT);
        Thread stderr = streamToLog(process.getErrorStream(), taskId, TaskLogWriter.STDERR);

        boolean finishedInTime = process.waitFor(script.timeoutSeconds(), TimeUnit.SECONDS);
        if (!finishedInTime) {
            taskLog.append(taskId, TaskLogWriter.SYSTEM,
                    "Przekroczono limit " + script.timeoutSeconds() + " s — ubijam drzewo procesów");
            killProcessTree(process);
            process.waitFor(5, TimeUnit.SECONDS);
            joinQuietly(stdout);
            joinQuietly(stderr);
            return new RunResult(-1, true);
        }

        joinQuietly(stdout);
        joinQuietly(stderr);
        return new RunResult(process.exitValue(), false);
    }

    private Thread streamToLog(InputStream inputStream, long taskId, String stream) {
        Thread thread = new Thread(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(inputStream, consoleCharset))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    taskLog.append(taskId, stream, line);
                }
            } catch (IOException e) {
                log.debug("Strumień {} zadania {} zamknięty: {}", stream, taskId, e.getMessage());
            }
        }, "task-" + taskId + "-" + stream.toLowerCase());
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void killProcessTree(Process process) {
        ProcessHandle handle = process.toHandle();
        handle.descendants().forEach(ProcessHandle::destroyForcibly); // najpierw dzieci!
        handle.destroyForcibly();
    }

    private static void joinQuietly(Thread thread) {
        try {
            thread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
