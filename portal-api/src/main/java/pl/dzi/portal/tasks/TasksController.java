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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import pl.dzi.portal.infrastructure.audit.AuditContext;
import pl.dzi.portal.infrastructure.audit.Audited;
import pl.dzi.portal.infrastructure.security.PortalUser;
import pl.dzi.portal.infrastructure.web.PortalRequestAttributes;

import java.util.List;

/**
 * Zlecenie: twardy RBAC jak w tiles (@PreAuthorize -> AccessFacade.canExecute).
 * Correlation id żądania HTTP wędruje do tasks.correlation_id — wpis audytu,
 * linie logu workera i status zadania kleją się jednym identyfikatorem.
 *
 * Polling statusu/logu celowo BEZ @Audited: warstwa HTTP audytu i tak zapisuje
 * każde żądanie (path z id zadania), a słownik akcji zostaje krótki.
 */
@RestController
@RequiredArgsConstructor
class TasksController {

    private final TasksFacade tasksFacade;
    private final AuditContext auditContext;

    @Audited(action = "TILE_EXECUTE")
    @PreAuthorize("@access.canExecute(#code, authentication)")
    @PostMapping("/api/tiles/{code}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    TasksFacade.TaskSubmitted run(@PathVariable String code, Authentication authentication,
                                  HttpServletRequest request) {
        String correlationId = (String) request.getAttribute(PortalRequestAttributes.CORRELATION_ID);
        var submitted = tasksFacade.submit(code, (PortalUser) authentication.getPrincipal(), correlationId);
        auditContext.setObjectRef("task:" + submitted.taskId());
        return submitted;
    }

    @GetMapping("/api/tasks/{id}")
    TasksFacade.TaskStatusResponse status(@PathVariable long id, Authentication authentication) {
        return tasksFacade.status(id, (PortalUser) authentication.getPrincipal());
    }

    @GetMapping("/api/tasks/{id}/log")
    List<TasksFacade.TaskLogLine> log(@PathVariable long id,
                                      @RequestParam(defaultValue = "0") long afterId,
                                      Authentication authentication) {
        return tasksFacade.log(id, afterId, (PortalUser) authentication.getPrincipal());
    }
}
