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
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import pl.dzi.portal.common.audit.AuditEntry;
import pl.dzi.portal.common.audit.AuditStatus;
import pl.dzi.portal.common.audit.AuditWriter;
import pl.dzi.portal.infrastructure.web.PortalRequestAttributes;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Czyste testy jednostkowe: writer jako in-memory double (dziedziczenie zamiast mocków),
 * łańcuch jako lambda ustawiająca status/atrybuty.
 */
class AuditFilterTest {

    private final CapturingAuditWriter writer = new CapturingAuditWriter();
    private final AuditFilter filter = new AuditFilter(writer);

    @Test
    void should_write_success_entry_with_business_action_and_object_ref() throws Exception {
        // given
        var request = new MockHttpServletRequest("POST", "/api/tiles/7/run");
        request.setRemoteAddr("127.0.0.1");
        request.setAttribute(PortalRequestAttributes.USERNAME, "jkowalski");
        request.setAttribute(PortalRequestAttributes.CORRELATION_ID, "cid-123");
        var response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            // symulacja: interceptor + kontroler ustawiły akcję i obiekt, odpowiedź 200
            req.setAttribute(PortalRequestAttributes.ACTION, "TILE_EXECUTE");
            req.setAttribute(PortalRequestAttributes.OBJECT_REF, "tile:7");
            ((HttpServletResponse) res).setStatus(200);
        };

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(writer.entries).hasSize(1);
        AuditEntry entry = writer.entries.get(0);
        assertThat(entry.username()).isEqualTo("jkowalski");
        assertThat(entry.httpMethod()).isEqualTo("POST");
        assertThat(entry.path()).isEqualTo("/api/tiles/7/run");
        assertThat(entry.action()).isEqualTo("TILE_EXECUTE");
        assertThat(entry.objectRef()).isEqualTo("tile:7");
        assertThat(entry.status()).isEqualTo(AuditStatus.SUCCESS);
        assertThat(entry.httpStatus()).isEqualTo(200);
        assertThat(entry.correlationId()).isEqualTo("cid-123");
    }

    @Test
    void should_map_403_to_denied_and_missing_user_to_dash() throws Exception {
        // given: odmowa zanim ustalono tożsamość (np. same-origin) — brak atrybutu USERNAME
        var request = new MockHttpServletRequest("POST", "/api/tiles/7/run");
        var response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((HttpServletResponse) res).setStatus(403);

        // when
        filter.doFilter(request, response, chain);

        // then
        AuditEntry entry = writer.entries.get(0);
        assertThat(entry.username()).isEqualTo("-");
        assertThat(entry.status()).isEqualTo(AuditStatus.DENIED);
        assertThat(entry.httpStatus()).isEqualTo(403);
    }

    @Test
    void should_map_500_to_error() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/whoami");
        var response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((HttpServletResponse) res).setStatus(500);

        filter.doFilter(request, response, chain);

        assertThat(writer.entries.get(0).status()).isEqualTo(AuditStatus.ERROR);
    }

    @Test
    void should_skip_audit_entry_for_304_not_modified() throws Exception {
        // polling ETag: 304 nie niesie danych i nie jest zdarzeniem — zero wpisu
        var request = new MockHttpServletRequest("GET", "/api/datasets/licencje");
        var response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((HttpServletResponse) res).setStatus(304);

        filter.doFilter(request, response, chain);

        assertThat(writer.entries).isEmpty();
    }

    @Test
    void should_not_break_request_when_audit_write_fails() throws Exception {
        // given
        writer.failOnWrite = true;
        var request = new MockHttpServletRequest("GET", "/api/whoami");
        var response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((HttpServletResponse) res).setStatus(200);

        // when / then: ADR-0001 — awaria audytu nie może położyć żądania
        assertThatCode(() -> filter.doFilter(request, response, chain)).doesNotThrowAnyException();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    /** In-memory double: AuditWriter celowo jest klasą — nadpisujemy write(), pola bazowe nieużywane. */
    private static final class CapturingAuditWriter extends AuditWriter {
        private final List<AuditEntry> entries = new ArrayList<>();
        private boolean failOnWrite = false;

        private CapturingAuditWriter() {
            super(null, null);
        }

        @Override
        public void write(AuditEntry entry) {
            if (failOnWrite) {
                throw new RuntimeException("symulowana awaria bazy audytu");
            }
            entries.add(entry);
        }
    }
}
