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

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import pl.dzi.portal.infrastructure.web.PortalRequestAttributes;

/**
 * Dynamiczne wzbogacanie bieżącego wpisu audytu z kodu kontrolera/fasady:
 *
 *   auditContext.setObjectRef("tile:" + tile.code());
 *
 * Działa wyłącznie w wątku żądania HTTP (RequestContextHolder) — worker nie używa
 * tej klasy; jego "audytem" są tabele tasks/task_log, a ewentualne wpisy do
 * audit_log robi bezpośrednio przez AuditWriter.
 */
@Component
public class AuditContext {

    /** Identyfikator obiektu, którego dotyczy akcja — konwencja "typ:id", np. "tile:42". */
    public void setObjectRef(String objectRef) {
        currentRequest().setAttribute(PortalRequestAttributes.OBJECT_REF, objectRef, RequestAttributes.SCOPE_REQUEST);
    }

    /** Nadpisanie nazwy akcji z @Audited — rzadkie (np. jedna metoda, dwie ścieżki biznesowe). */
    public void setAction(String action) {
        currentRequest().setAttribute(PortalRequestAttributes.ACTION, action, RequestAttributes.SCOPE_REQUEST);
    }

    private static RequestAttributes currentRequest() {
        return RequestContextHolder.currentRequestAttributes();
    }
}
