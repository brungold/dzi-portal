/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.dzi.portal.infrastructure.web.ClientIpResolver;
import pl.dzi.portal.infrastructure.web.PortalRequestAttributes;

import java.io.IOException;
import java.util.Set;

/**
 * Uwierzytelnianie pre-authenticated na podstawie nagłówka wstrzykiwanego przez IIS
 * po udanym handshake'u Kerberos (wariant A architektury).
 *
 * Granica zaufania — trzy warstwy:
 *  1) server.address=127.0.0.1 — aplikacja w ogóle nie słucha poza loopbackiem,
 *  2) ten filtr honoruje nagłówek WYŁĄCZNIE z loopbacku; nagłówek z innego adresu = 401 + WARN,
 *  3) reguła URL Rewrite w IIS ustawia nagłówek bezwarunkowo, nadpisując cokolwiek przysłał klient.
 *
 * Celowo NIE jest to bean Springa — instancję tworzy SecurityConfig. Gdyby filtr był @Componentem,
 * Boot zarejestrowałby go dodatkowo jako zwykły filtr serwletowy i wykonywałby się dwukrotnie.
 */
@Slf4j
final class LoopbackHeaderAuthenticationFilter extends OncePerRequestFilter {

    private final AdGroupResolver adGroupResolver;
    private final PortalSecurityProperties properties;

    LoopbackHeaderAuthenticationFilter(AdGroupResolver adGroupResolver, PortalSecurityProperties properties) {
        this.adGroupResolver = adGroupResolver;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        boolean loopback = ClientIpResolver.isLoopback(request.getRemoteAddr());
        String rawHeader = request.getHeader(properties.header());

        if (rawHeader == null && loopback && StringUtils.hasText(properties.devFallbackUser())) {
            rawHeader = properties.devFallbackUser();
        }
        if (rawHeader == null) {
            // Brak tożsamości — o odmowie zdecyduje autoryzacja (401 z ApiAuthenticationEntryPoint).
            filterChain.doFilter(request, response);
            return;
        }

        String login = extractSamAccountName(rawHeader);
        request.setAttribute(PortalRequestAttributes.USERNAME, login); // także dla żądań odrzuconych — audyt prób

        if (!loopback) {
            log.warn("Odrzucono nagłówek {} spoza loopbacku: remoteAddr={}, login={}",
                    properties.header(), request.getRemoteAddr(), login);
            SecurityContextHolder.clearContext();
            writeUnauthorized(response);
            return;
        }

        Set<String> groups = adGroupResolver.resolveGroups(login);
        var principal = new PortalUser(login, groups);
        var authorities = groups.stream().map(SimpleGrantedAuthority::new).toList();
        var authentication = new PreAuthenticatedAuthenticationToken(principal, "N/A", authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    /** "DZI\jkowalski" -> "jkowalski"; "jkowalski@dzi.pl" -> "jkowalski" */
    private static String extractSamAccountName(String raw) {
        String value = raw.trim();
        int backslash = value.lastIndexOf('\\');
        if (backslash >= 0) {
            value = value.substring(backslash + 1);
        }
        int at = value.indexOf('@');
        if (at > 0) {
            value = value.substring(0, at);
        }
        return value;
    }

    private static void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"title\":\"Unauthorized\",\"status\":401}");
    }
}
