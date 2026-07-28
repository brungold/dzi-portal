/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Ochrona CSRF dopasowana do SSO Kerberos: przeglądarka uwierzytelnia się "ambientowo"
 * (bez cookies), więc klasyczny token CSRF nie ma nośnika, a strona z internetu mogłaby
 * wywołać w tle żądanie mutujące — przeglądarka w strefie Intranet dołoży bilet Kerberos.
 *
 * Współczesne przeglądarki wysyłają Sec-Fetch-Site; jawne "cross-site" dla metod mutujących
 * blokujemy. Brak nagłówka (curl, testy, starzy klienci) przepuszczamy — to defense-in-depth,
 * a nie jedyna linia obrony.
 */
@Slf4j
public final class SameOriginRequestFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final String SEC_FETCH_SITE = "Sec-Fetch-Site";
    private static final String CROSS_SITE = "cross-site";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String secFetchSite = request.getHeader(SEC_FETCH_SITE);
        boolean mutating = !SAFE_METHODS.contains(request.getMethod());

        if (mutating && CROSS_SITE.equalsIgnoreCase(secFetchSite)) {
            log.warn("Odrzucono żądanie cross-site: {} {}", request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/problem+json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"title\":\"Żądanie cross-site odrzucone\",\"status\":403}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
