/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.infrastructure.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.dzi.portal.common.audit.AuditEntry;
import pl.dzi.portal.common.audit.AuditStatus;
import pl.dzi.portal.common.audit.AuditWriter;
import pl.dzi.portal.infrastructure.web.ClientIpResolver;
import pl.dzi.portal.infrastructure.web.PortalRequestAttributes;

import java.io.IOException;

/**
 * Audyt KAŻDEGO żądania /api/*. Filtr jest zarejestrowany PRZED łańcuchem Spring Security
 * (WebFiltersConfiguration), więc oplata go w całości: mierzy pełny czas i widzi też odmowy
 * (401/403) — próby obejścia UI to dokładnie to, co audytor chce zobaczyć.
 *
 * Wpis składa się z warstwy HTTP (zawsze) oraz — jeśli żądanie doszło do warstwy MVC —
 * akcji biznesowej (@Audited) i identyfikatora obiektu (AuditContext), czytanych
 * z atrybutów requestu. Login również z atrybutu, bo SecurityContext jest czyszczony
 * zanim sterowanie tu wróci.
 */
@Slf4j
@RequiredArgsConstructor
public final class AuditFilter extends OncePerRequestFilter {

    private static final String UNKNOWN_USER = "-";

    private final AuditWriter auditWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            // 304 = "nic się nie wydarzyło" (polling ETag) — bez wpisu. Zwykły warunek,
            // celowo NIE wczesny return: return w bloku finally POŁYKA wyjątek lecący
            // z łańcucha (poprawka z drugiego przeglądu, commit 32).
            if (response.getStatus() != HttpServletResponse.SC_NOT_MODIFIED) {
                writeEntrySafely(request, response, durationMs);
            }
        }
    }

    private void writeEntrySafely(HttpServletRequest request, HttpServletResponse response, long durationMs) {
        try {
            auditWriter.write(buildEntry(request, response, durationMs));
        } catch (Exception e) {
                // Decyzja (ADR-0001): awaria zapisu audytu nie blokuje odpowiedzi użytkownika,
                // ale krzyczy w logach — to sygnał do natychmiastowej interwencji.
            log.error("Zapis audytu nie powiódł się dla {} {} (żądanie NIE zostało zablokowane)",
                    request.getMethod(), request.getRequestURI(), e);
        }
    }

    private AuditEntry buildEntry(HttpServletRequest request, HttpServletResponse response, long durationMs) {
        String username = (String) request.getAttribute(PortalRequestAttributes.USERNAME);
        String correlationId = (String) request.getAttribute(PortalRequestAttributes.CORRELATION_ID);
        String action = (String) request.getAttribute(PortalRequestAttributes.ACTION);
        String objectRef = (String) request.getAttribute(PortalRequestAttributes.OBJECT_REF);
        int httpStatus = response.getStatus();

        return new AuditEntry(
                username != null ? username : UNKNOWN_USER,
                ClientIpResolver.resolve(request),
                request.getMethod(),
                request.getRequestURI(),           // celowo bez query stringa — parametry poza audytem HTTP
                action,
                objectRef,
                toAuditStatus(httpStatus),
                httpStatus,
                durationMs,
                correlationId != null ? correlationId : "");
    }

    private static AuditStatus toAuditStatus(int httpStatus) {
        if (httpStatus < 400) {
            return AuditStatus.SUCCESS;
        }
        if (httpStatus == 401 || httpStatus == 403) {
            return AuditStatus.DENIED;
        }
        return AuditStatus.ERROR;
    }
}
