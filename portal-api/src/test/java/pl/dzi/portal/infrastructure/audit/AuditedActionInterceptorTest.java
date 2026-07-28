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

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import pl.dzi.portal.infrastructure.web.PortalRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class AuditedActionInterceptorTest {

    private final AuditedActionInterceptor interceptor = new AuditedActionInterceptor();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void should_copy_action_from_annotation_to_request_attribute() throws Exception {
        var handler = new HandlerMethod(new DummyController(), DummyController.class.getMethod("annotated"));

        boolean proceed = interceptor.preHandle(request, response, handler);

        assertThat(proceed).isTrue();
        assertThat(request.getAttribute(PortalRequestAttributes.ACTION)).isEqualTo("TEST_ACTION");
    }

    @Test
    void should_leave_attribute_empty_for_method_without_annotation() throws Exception {
        var handler = new HandlerMethod(new DummyController(), DummyController.class.getMethod("plain"));

        interceptor.preHandle(request, response, handler);

        assertThat(request.getAttribute(PortalRequestAttributes.ACTION)).isNull();
    }

    @Test
    void should_ignore_non_handler_method_handlers() {
        // np. ResourceHttpRequestHandler dla statyki — nie może wybuchnąć
        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isTrue();
        assertThat(request.getAttribute(PortalRequestAttributes.ACTION)).isNull();
    }

    static class DummyController {
        @Audited(action = "TEST_ACTION")
        public void annotated() {
        }

        public void plain() {
        }
    }
}
