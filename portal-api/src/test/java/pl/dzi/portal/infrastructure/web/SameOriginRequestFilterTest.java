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

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SameOriginRequestFilterTest {

    private final SameOriginRequestFilter filter = new SameOriginRequestFilter();

    @Test
    void should_block_mutating_cross_site_request() throws Exception {
        // given
        var request = new MockHttpServletRequest("POST", "/api/tiles/1/run");
        request.addHeader("Sec-Fetch-Site", "cross-site");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void should_allow_mutating_same_origin_request() throws Exception {
        // given
        var request = new MockHttpServletRequest("POST", "/api/tiles/1/run");
        request.addHeader("Sec-Fetch-Site", "same-origin");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void should_allow_cross_site_get_request() throws Exception {
        // given
        var request = new MockHttpServletRequest("GET", "/api/tiles");
        request.addHeader("Sec-Fetch-Site", "cross-site");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void should_allow_request_without_sec_fetch_site_header() throws Exception {
        // given: curl / klienci nie-przeglądarkowi nie wysyłają tego nagłówka
        var request = new MockHttpServletRequest("POST", "/api/tiles/1/run");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(chain.getRequest()).isNotNull();
    }
}
