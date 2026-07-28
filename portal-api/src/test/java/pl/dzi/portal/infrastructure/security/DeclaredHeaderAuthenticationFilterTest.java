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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import pl.dzi.portal.infrastructure.web.PortalRequestAttributes;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DeclaredHeaderAuthenticationFilterTest {

    private static final String HEADER = "X-Auth-User";

    private final AdGroupResolver resolver = samAccountName -> Set.of("dzi", "wszyscy");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_authenticate_declared_login_from_allowed_cidr() throws Exception {
        // given
        var filter = filter(List.of("10.20.0.0/16"));
        var request = requestFrom("10.20.1.5");
        request.addHeader(HEADER, "DZI\\jkowalski");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(((PortalUser) authentication.getPrincipal()).login()).isEqualTo("jkowalski");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("dzi", "wszyscy");
        assertThat(chain.getRequest()).as("żądanie poszło dalej w łańcuch").isNotNull();
        assertThat(request.getAttribute(PortalRequestAttributes.USERNAME)).isEqualTo("jkowalski");
    }

    @Test
    void should_authenticate_from_loopback_even_with_empty_cidrs() throws Exception {
        // pusta lista CIDR = zachowanie jak wariant A: ufamy wyłącznie loopbackowi
        var filter = filter(List.of());
        var request = requestFrom("127.0.0.1");
        request.addHeader(HEADER, "jkowalski");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void should_reject_declaration_from_outside_trusted_ranges() throws Exception {
        // given
        var filter = filter(List.of("10.20.0.0/16"));
        var request = requestFrom("192.168.77.10");
        request.addHeader(HEADER, "jkowalski");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).as("żądanie NIE poszło dalej w łańcuch").isNull();
        assertThat(request.getAttribute(PortalRequestAttributes.USERNAME))
                .as("login zapisany dla audytu prób")
                .isEqualTo("jkowalski");
    }

    @Test
    void should_not_apply_any_fallback_user_when_header_missing() throws Exception {
        // w declared brak nagłówka = brak tożsamości, nawet z loopbacku (celowo bez fallbacku)
        var filter = filter(List.of());
        var request = requestFrom("127.0.0.1");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).as("dalej decyduje autoryzacja (401 z entry pointu)").isNotNull();
    }

    @Test
    void should_return_429_when_rate_limiter_blocks() throws Exception {
        // limiter z sufitem 1/min: pierwsze żądanie przechodzi, drugie dostaje 429
        var strictLimiter = new DeclaredRateLimiter(
                properties(List.of(), 1), Clock.systemUTC());
        var filter = new DeclaredHeaderAuthenticationFilter(
                resolver, securityProperties(), properties(List.of(), 1), strictLimiter);

        var first = requestFrom("127.0.0.1");
        first.addHeader(HEADER, "jkowalski");
        filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());
        SecurityContextHolder.clearContext();

        var second = requestFrom("127.0.0.1");
        second.addHeader(HEADER, "jkowalski");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(second, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNull();
        assertThat(second.getAttribute(PortalRequestAttributes.USERNAME))
                .as("429 też ma trafić do audytu z loginem")
                .isEqualTo("jkowalski");
    }

    private DeclaredHeaderAuthenticationFilter filter(List<String> allowedCidrs) {
        var declaredProperties = properties(allowedCidrs, 1000);
        return new DeclaredHeaderAuthenticationFilter(
                resolver,
                securityProperties(),
                declaredProperties,
                new DeclaredRateLimiter(declaredProperties, Clock.systemUTC()));
    }

    private static DeclaredIdentityProperties properties(List<String> allowedCidrs, int maxPerMinute) {
        return new DeclaredIdentityProperties(
                allowedCidrs, maxPerMinute, 3, Duration.ofMinutes(10), Duration.ofMinutes(15));
    }

    private static PortalSecurityProperties securityProperties() {
        return new PortalSecurityProperties(HEADER, null, Duration.ofMinutes(10), java.util.Map.of());
    }

    private static MockHttpServletRequest requestFrom(String remoteAddr) {
        var request = new MockHttpServletRequest("GET", "/api/tiles");
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
