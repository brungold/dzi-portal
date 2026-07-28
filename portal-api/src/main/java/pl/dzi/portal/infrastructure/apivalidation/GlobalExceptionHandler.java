/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.infrastructure.apivalidation;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.dzi.portal.infrastructure.web.PortalRequestAttributes;

/**
 * Jeden format błędu dla całego API: application/problem+json (RFC 9457).
 */
@Slf4j
@RestControllerAdvice
class GlobalExceptionHandler {

    /**
     * AccessDeniedException (np. z @PreAuthorize) nie może wpaść w catch-all i zamienić się
     * w 500 — mapujemy jawnie na 403; AuditFilter zapisze wpis DENIED.
     */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setTitle("Brak uprawnień");
        return problem;
    }

    /**
     * Przegrany wyścig edycji (@Version w Data JDBC): ktoś zapisał wiersz między odczytem
     * a zapisem przegranego. 409 + instrukcja — frontend odświeża widok.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Dane zmienione przez kogoś innego");
        problem.setDetail("Ktoś zapisał ten wiersz w trakcie Twojej edycji — odśwież widok i wprowadź zmianę ponownie.");
        return problem;
    }

    /**
     * Złe wejścia klienta (commit 32): nienumeryczne id w ścieżce, zepsuty JSON,
     * brak parametru/pliku. Bez tych handlerów wpadały w catch-all i wracały jako 500
     * z pełnym stack trace w logu — a to wina żądania, nie serwera.
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class})
    ProblemDetail handleBadClientInput(Exception ex, HttpServletRequest request) {
        log.warn("Odrzucono nieprawidłowe żądanie {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Nieprawidłowe żądanie");
        problem.setDetail("Sprawdź format parametrów i treści żądania.");
        return problem;
    }

    /** Wyjątki niosące własny status (ResponseStatusException itp.) przechodzą bez zmian. */
    @ExceptionHandler(ErrorResponseException.class)
    ProblemDetail handleErrorResponse(ErrorResponseException ex) {
        return ex.getBody();
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Nieobsłużony błąd: {} {}", request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Błąd wewnętrzny");
        Object correlationId = request.getAttribute(PortalRequestAttributes.CORRELATION_ID);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
        return problem;
    }
}
