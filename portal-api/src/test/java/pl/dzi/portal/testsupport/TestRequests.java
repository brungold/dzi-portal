package pl.dzi.portal.testsupport;

import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * Wspólny budowniczy żądań w testach plasterkowych: nagłówek tożsamości + loopback
 * (poprawka DRY z przeglądu, commit 31 — wcześniej trzy identyczne prywatne helpery).
 *
 * Generyk po AbstractMockHttpServletRequestBuilder: w Spring Framework 7 budowniczy
 * multipart nie dziedziczy już po MockHttpServletRequestBuilder — oba mają wspólną
 * klasę bazową. Dzięki temu jedna metoda obsługuje get/post/patch ORAZ multipart,
 * a wywołania w testach zostają bez zmian (zwracany typ = typ przekazany).
 */
public final class TestRequests {

    private TestRequests() {
    }

    public static <B extends AbstractMockHttpServletRequestBuilder<B>> B asUser(B request, String login) {
        return request
                .header("X-Auth-User", "DZI\\" + login)
                .with(mockRequest -> {
                    mockRequest.setRemoteAddr("127.0.0.1");
                    return mockRequest;
                });
    }
}