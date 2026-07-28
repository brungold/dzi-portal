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
 * Kontrakt z portal-bootstrap.js. Flagi canExecute/canEdit pozwalają frontendowi
 * pokazać kafelek "widzę, ale nie uruchomię" (READ bez EXECUTE) bez drugiego zapytania.
 */
public record TileResponse(
        String code,
        String name,
        String description,
        String icon,
        String tileType,
        String actionRef,
        boolean canExecute,
        boolean canEdit) {
}
