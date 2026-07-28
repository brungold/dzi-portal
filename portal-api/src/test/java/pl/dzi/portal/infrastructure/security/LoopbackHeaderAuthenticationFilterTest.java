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

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Czyste testy jednostkowe filtra — bez kontekstu Springa, resolver jako in-memory double.
 */
class LoopbackHeaderAuthenticationFilterTest {

    private static final String HEADER = "X-Auth-User";

    private final AdGroupResolver resolver = samAccountName -> Set.of("DZI-Portal-Admin");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_authenticate_user_when_header_comes_from_loopback() throws Exception {
        // given
        var filter = filterWithFallback(null);
        var request = requestFrom("127.0.0.1");
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
                .containsExactly("DZI-Portal-Admin");
        assertThat(chain.getRequest()).as("żądanie poszło dalej w łańcuch").isNotNull();
        assertThat(request.getAttribute(PortalRequestAttributes.USERNAME)).isEqualTo("jkowalski");
    }

    @Test
    void should_reject_request_when_header_present_but_not_from_loopback() throws Exception {
        // given
        var filter = filterWithFallback(null);
        var request = requestFrom("10.1.2.3");
        request.addHeader(HEADER, "DZI\\jkowalski");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).as("żądanie NIE poszło dalej w łańcuch").isNull();
    }

    @Test
    void should_continue_without_authentication_when_header_absent() throws Exception {
        // given
        var filter = filterWithFallback(null);
        var request = requestFrom("127.0.0.1");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).as("o odmowie decyduje autoryzacja, nie ten filtr").isNotNull();
    }

    @Test
    void should_use_dev_fallback_user_when_header_absent_and_fallback_configured() throws Exception {
        // given
        var filter = filterWithFallback("tester");
        var request = requestFrom("127.0.0.1");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(((PortalUser) authentication.getPrincipal()).login()).isEqualTo("tester");
    }

    @Test
    void should_strip_domain_prefix_and_upn_suffix_from_login() throws Exception {
        // given
        var filter = filterWithFallback(null);
        var request = requestFrom("127.0.0.1");
        request.addHeader(HEADER, "jkowalski@dzi.pl");
        var response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, new MockFilterChain());

        // then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(((PortalUser) authentication.getPrincipal()).login()).isEqualTo("jkowalski");
    }

    private LoopbackHeaderAuthenticationFilter filterWithFallback(String devFallbackUser) {
        var properties = new PortalSecurityProperties(HEADER, devFallbackUser, Duration.ofMinutes(10), Map.of());
        return new LoopbackHeaderAuthenticationFilter(resolver, properties);
    }

    private static MockHttpServletRequest requestFrom(String remoteAddr) {
        var request = new MockHttpServletRequest("GET", "/api/whoami");
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
