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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Deklaratywna nazwa akcji biznesowej dla wpisu audytu. Adnotację czyta
 * AuditedActionInterceptor (zwykły HandlerInterceptor MVC — zero proxy AOP,
 * więc bez pułapek typu self-invocation).
 *
 * Ważna własność: interceptor działa w preHandle, PRZED autoryzacją metodową
 * (@PreAuthorize), więc także ODMOWY mają wypełnione action — audytor widzi,
 * co konkretnie próbowano zrobić, nie tylko "403 na jakimś URL-u".
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** Stała nazwa akcji w UPPER_SNAKE_CASE, np. TILE_EXECUTE, DATASET_IMPORT. Słownik trzymamy krótki. */
    String action();
}
