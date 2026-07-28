/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.tiles;

/**
 * Poziomy uprawnień do kafelka. Hierarchia LINIOWA (świadome uproszczenie
 * dla zdrowia psychicznego administratora): READ < EXECUTE < EDIT.
 * Jedno nadanie EXECUTE wystarcza, żeby kafelek było też widać.
 */
enum PermissionLevel {
    READ,
    EXECUTE,
    EDIT;

    /** Czy TEN poziom zaspokaja poziom wymagany (np. EDIT pokrywa READ i EXECUTE). */
    boolean covers(PermissionLevel required) {
        return this.ordinal() >= required.ordinal();
    }
}
