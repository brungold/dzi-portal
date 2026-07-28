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

/**
 * Nazwy atrybutów requestu przekazywanych między filtrami i warstwą MVC
 * (correlation -> security -> interceptor/kontroler -> audyt).
 * Jedno miejsce zamiast rozsianych literałów.
 */
public final class PortalRequestAttributes {

    public static final String USERNAME = "portal.audit.username";
    public static final String CORRELATION_ID = "portal.correlationId";
    /** Nazwa akcji biznesowej — ustawiana deklaratywnie przez @Audited (interceptor). */
    public static final String ACTION = "portal.audit.action";
    /** Identyfikator obiektu akcji — ustawiany dynamicznie przez AuditContext. */
    public static final String OBJECT_REF = "portal.audit.objectRef";

    private PortalRequestAttributes() {
    }
}
