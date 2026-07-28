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
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.dzi.portal.infrastructure.web.ClientIpResolver;
import pl.dzi.portal.infrastructure.web.PortalRequestAttributes;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Profil {@code declared}: tożsamość jest DEKLAROWANA nagłówkiem przez klienta
 * (skrypt PowerShell, przeglądarka), NIE uwierzytelniana — patrz ADR-0003.
 *
 * Klient deklaruje WYŁĄCZNIE login. Wszystko, co ma skutki autoryzacyjne
 * (departamenty, uprawnienia do kafelków), serwer wyprowadza sam:
 * login -> user_departments (baza) -> tile_permissions -> AccessFacade.
 * Departament przysłany w żądaniu nie istnieje w tym modelu — nie ma czego fałszować.
 *
 * Różnice względem LoopbackHeaderAuthenticationFilter (wariant A, prod):
 *  - zaufanie: loopback LUB skonfigurowane CIDR-y (puste CIDR-y = tylko loopback),
 *  - BRAK dev-fallbacku — deklaracja musi być jawna, żeby audyt wiązał żądanie z loginem,
 *  - przed uwierzytelnieniem: limiter częstotliwości + detekcja anomalii (429).
 *
 * Celowo NIE jest to bean Springa — instancję tworzy DeclaredSecurityConfiguration
 * (ta sama pułapka podwójnej rejestracji, co przy filtrze wariantu A).
 */
@Slf4j
final class DeclaredHeaderAuthenticationFilter extends OncePerRequestFilter {

    private final AdGroupResolver groupResolver;
    private final PortalSecurityProperties securityProperties;
    private final DeclaredRateLimiter rateLimiter;
    private final List<IpAddressMatcher> trustedRanges;

    DeclaredHeaderAuthenticationFilter(AdGroupResolver groupResolver,
                                       PortalSecurityProperties securityProperties,
                                       DeclaredIdentityProperties declaredProperties,
                                       DeclaredRateLimiter rateLimiter) {
        this.groupResolver = groupResolver;
        this.securityProperties = securityProperties;
        this.rateLimiter = rateLimiter;
        this.trustedRanges = declaredProperties.allowedCidrs().stream()
                .map(IpAddressMatcher::new)
                .toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String rawHeader = request.getHeader(securityProperties.header());
        if (rawHeader == null) {
            // Brak deklaracji — o odmowie zdecyduje autoryzacja (401 z ApiAuthenticationEntryPoint).
            // Świadomie BEZ dev-fallbacku: w tym trybie każdy wpis audytu ma nieść jawny login.
            filterChain.doFilter(request, response);
            return;
        }

        String login = extractSamAccountName(rawHeader);
        request.setAttribute(PortalRequestAttributes.USERNAME, login); // także dla odrzuconych — audyt prób

        if (!isTrustedSource(request.getRemoteAddr())) {
            log.warn("Odrzucono deklarację tożsamości spoza zaufanych zakresów: remoteAddr={}, login={}",
                    request.getRemoteAddr(), login);
            SecurityContextHolder.clearContext();
            writeProblem(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return;
        }

        // Limiter PO teście zaufania (spoza zakresów i tak 401), PRZED sięgnięciem do bazy
        // po grupy — odcięty adres nie generuje kosztu zapytań. Klucz = ClientIpResolver.resolve,
        // czyli XFF honorowany wyłącznie zza loopbacku (zaufane proxy na tej samej maszynie).
        DeclaredRateLimiter.Decision decision = rateLimiter.register(ClientIpResolver.resolve(request), login);
        if (decision != DeclaredRateLimiter.Decision.ALLOW) {
            SecurityContextHolder.clearContext();
            response.setHeader("Retry-After", "60");
            writeProblem(response, 429, "Zbyt wiele żądań");
            return; // 429 -> AuditFilter zapisze wpis ERROR z deklarowanym loginem i adresem
        }

        Set<String> groups = groupResolver.resolveGroups(login);
        var principal = new PortalUser(login, groups);
        var authorities = groups.stream().map(SimpleGrantedAuthority::new).toList();
        var authentication = new PreAuthenticatedAuthenticationToken(principal, "N/A", authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private boolean isTrustedSource(String remoteAddr) {
        if (ClientIpResolver.isLoopback(remoteAddr)) {
            return true;
        }
        return trustedRanges.stream().anyMatch(range -> range.matches(remoteAddr));
    }

    /**
     * "DZI\jkowalski" -> "jkowalski"; "jkowalski@dzi.pl" -> "jkowalski".
     * Świadoma kopia prywatnej metody z LoopbackHeaderAuthenticationFilter — wyniesienie
     * wymagałoby zmiany pliku wariantu A, a paczka 38 nie dotyka istniejących plików.
     */
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

    private static void writeProblem(HttpServletResponse response, int status, String title) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"title\":\"" + title + "\",\"status\":" + status + "}");
    }
}
