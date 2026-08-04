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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Profil {@code declared}: tożsamość jest DEKLAROWANA nagłówkami przez klienta
 * (skrypt PowerShell, przeglądarka), NIE uwierzytelniana — ADR-0003 (granice
 * zaufania, limiter, audyt) + ADR-0005 (model przynależności).
 *
 * Klient deklaruje LOGIN ({@code X-Auth-User}) i DEPARTAMENT ({@code X-Auth-Dept},
 * skrót z AD extensionattribute12). Serwer NICZEGO nie weryfikuje w katalogu ani
 * w bazie — zbiór uprawnień żądania to {login, departament, "wszyscy"}, porównywany
 * z tile_permissions.ad_group (AccessFacade, case-insensitive). Dzięki temu
 * uprawnienia kafelka nadaje się departamentowi, loginowi imiennie albo miksem —
 * bez rejestru użytkowników po stronie portalu (uzasadnienie skali: ADR-0005).
 *
 * Konsekwencja wprost: kafelek jest widoczny dla każdego, kto zna adres portalu
 * i wpisze właściwy departament. tile_permissions PORZĄDKUJE widoczność, nie chroni
 * danych — stąd bezwzględny zakaz danych wrażliwych w tym trybie.
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

    /** Syntetyczna grupa doklejana każdej deklaracji — kafelek "dla wszystkich"
     *  to jeden wiersz tile_permissions z ad_group='wszyscy'. Po ADR-0005 oznacza
     *  KAŻDEGO, kto dotrze do portalu (nie ma już rejestru "znanych" loginów). */
    static final String GROUP_EVERYONE = "wszyscy";

    /** Format skrótu departamentu po normalizacji (małe litery, bez spacji). */
    private static final Pattern DEPT_PATTERN = Pattern.compile("[a-z0-9._-]{1,64}");

    private final PortalSecurityProperties securityProperties;
    private final DeclaredIdentityProperties declaredProperties;
    private final DeclaredRateLimiter rateLimiter;
    private final List<IpAddressMatcher> trustedRanges;

    DeclaredHeaderAuthenticationFilter(PortalSecurityProperties securityProperties,
                                       DeclaredIdentityProperties declaredProperties,
                                       DeclaredRateLimiter rateLimiter) {
        this.securityProperties = securityProperties;
        this.declaredProperties = declaredProperties;
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

        Set<String> groups = declaredAuthorities(login, extractDepartment(request));
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
     * Małe litery: konwencja całego modelu (tile_permissions, audyt) — dzięki temu
     * uprawnienia imienne nie zależą od wielkości liter przysłanej przez klienta.
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
        return value.toLowerCase(Locale.ROOT);
    }

    /**
     * Deklarowany departament z nagłówka (ADR-0005): trim + małe litery.
     * Brak nagłówka = deklaracja samego loginu (uprawnienia: {login, wszyscy}).
     * Wartość poza formatem (spacje, polskie znaki — np. wklejona pełna nazwa
     * zamiast skrótu) jest IGNOROWANA z ostrzeżeniem w logu: użytkownik dostanie
     * mniej kafelków, a nie zagadkowy błąd — objaw jest widoczny i diagnozowalny.
     */
    private String extractDepartment(HttpServletRequest request) {
        String raw = request.getHeader(declaredProperties.deptHeader());
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String dept = raw.trim().toLowerCase(Locale.ROOT);
        if (!DEPT_PATTERN.matcher(dept).matches()) {
            log.warn("Zignorowano deklarowany departament w złym formacie: '{}' (oczekiwany skrót, np. dzi)", raw);
            return null;
        }
        return dept;
    }

    /** Zbiór uprawnień żądania: {login, departament?, wszyscy} — patrz javadoc klasy. */
    private static Set<String> declaredAuthorities(String login, String dept) {
        var groups = new LinkedHashSet<String>();
        groups.add(login);
        if (dept != null) {
            groups.add(dept);
        }
        groups.add(GROUP_EVERYONE);
        return Set.copyOf(groups);
    }

    private static void writeProblem(HttpServletResponse response, int status, String title) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"title\":\"" + title + "\",\"status\":" + status + "}");
    }
}
