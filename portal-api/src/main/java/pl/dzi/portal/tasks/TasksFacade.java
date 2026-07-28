/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.tasks;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import pl.dzi.portal.common.script.Script;
import pl.dzi.portal.common.task.Task;
import pl.dzi.portal.common.task.TaskLogEntry;
import pl.dzi.portal.common.task.TaskLogRepository;
import pl.dzi.portal.common.task.TaskRepository;
import pl.dzi.portal.infrastructure.security.PortalUser;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Zlecanie i podgląd zadań. Zlecający NIE wskazuje skryptu — wskazuje KAFELEK,
 * a skrypt wynika z whitelisty (tiles.action_ref -> scripts.code). Ścieżka z żądania
 * nie istnieje jako pojęcie.
 *
 * Podgląd: tylko właściciel zadania (requested_by). Cudze id => 403, nie 404 —
 * ta sama zasada co w AccessFacade: odmowa nie zdradza istnienia zasobu.
 */
@RequiredArgsConstructor
class TasksFacade {

    private final RunnableScriptRepository runnableScripts;
    private final TaskRepository tasks;
    private final TaskLogRepository taskLog;
    private final Clock clock;

    public record TaskSubmitted(long taskId, String status, String correlationId) {
    }

    public record TaskStatusResponse(long id, String status, Instant createdAt, Instant startedAt,
                                     Instant finishedAt, Integer exitCode, String correlationId) {
    }

    public record TaskLogLine(long id, Instant tsUtc, String stream, String line) {
    }

    TaskSubmitted submit(String tileCode, PortalUser user, String correlationId) {
        Script script = runnableScripts.findActiveScriptForTile(tileCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Kafelek nie ma aktywnego skryptu — zgłoś administratorowi portalu"));
        Long tileId = runnableScripts.findTileId(tileCode).orElse(null);

        Task saved = tasks.save(Task.pending(script.id(), tileId, user.login(),
                null /* parametry użytkownika: świadomie wyłączone do czasu walidacji JSON Schema */,
                correlationId, clock.instant()));
        return new TaskSubmitted(saved.id(), saved.status().name(), correlationId);
    }

    TaskStatusResponse status(long taskId, PortalUser user) {
        Task task = ownedTask(taskId, user);
        return new TaskStatusResponse(task.id(), task.status().name(), task.createdAt(),
                task.startedAt(), task.finishedAt(), task.exitCode(), task.correlationId());
    }

    List<TaskLogLine> log(long taskId, long afterId, PortalUser user) {
        ownedTask(taskId, user);
        return taskLog.findByTaskIdAfter(taskId, afterId).stream()
                .map(entry -> new TaskLogLine(entry.id(), entry.tsUtc(), entry.stream(), entry.line()))
                .toList();
    }

    private Task ownedTask(long taskId, PortalUser user) {
        Task task = tasks.findById(taskId)
                .orElseThrow(() -> new AccessDeniedException("Brak dostępu do zadania"));
        if (!task.requestedBy().equalsIgnoreCase(user.login())) {
            throw new AccessDeniedException("Brak dostępu do zadania");
        }
        return task;
    }
}
