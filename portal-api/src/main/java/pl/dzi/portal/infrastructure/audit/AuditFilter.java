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
 *
 * Żądanie przerwane wyjątkiem jest zapisywane jako ERROR/500 — status z response
 * w chwili przelotu wyjątku przez ten filtr jeszcze kłamie (kontener nada 500 wyżej).
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
        } catch (Exception e) {
            // Żądanie przerwane wyjątkiem. response.getStatus() pokazuje w tym momencie wartość
            // sprzed błędu (zwykle 200) — kod 500 nada dopiero kontener PIĘTRO WYŻEJ
            // (StandardWrapperValve -> error dispatch na /error). Zapisujemy więc prawdę
            // (ERROR/500) jawnie i puszczamy wyjątek dalej, żeby obsługa błędów zadziałała.
            //
            // Lekcja (2026-08-06): puste X-Auth-User z IIS kończyło się wyjątkiem w filtrze
            // uwierzytelniania, a audyt notował "SUCCESS 200" — rejestr kłamał przy awarii.
            writeEntrySafely(request, elapsedMs(startNanos), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            throw e;
        }
        // 304 = "nic się nie wydarzyło" (polling ETag) — bez wpisu. Wcześniejsza wersja używała
        // finally (stąd poprawka z commitu 32 o return-w-finally); po rozdzieleniu ścieżek
        // wyjątek ma własny zapis powyżej, a finally nie jest już potrzebne.
        if (response.getStatus() != HttpServletResponse.SC_NOT_MODIFIED) {
            writeEntrySafely(request, elapsedMs(startNanos), response.getStatus());
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private void writeEntrySafely(HttpServletRequest request, long durationMs, int httpStatus) {
        try {
            auditWriter.write(buildEntry(request, durationMs, httpStatus));
        } catch (Exception e) {
                // Decyzja (ADR-0001): awaria zapisu audytu nie blokuje odpowiedzi użytkownika,
                // ale krzyczy w logach — to sygnał do natychmiastowej interwencji.
            log.error("Zapis audytu nie powiódł się dla {} {} (żądanie NIE zostało zablokowane)",
                    request.getMethod(), request.getRequestURI(), e);
        }
    }

    private AuditEntry buildEntry(HttpServletRequest request, long durationMs, int httpStatus) {
        String username = (String) request.getAttribute(PortalRequestAttributes.USERNAME);
        String correlationId = (String) request.getAttribute(PortalRequestAttributes.CORRELATION_ID);
        String action = (String) request.getAttribute(PortalRequestAttributes.ACTION);
        String objectRef = (String) request.getAttribute(PortalRequestAttributes.OBJECT_REF);

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
