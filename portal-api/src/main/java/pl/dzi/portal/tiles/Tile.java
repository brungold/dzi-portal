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

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Kafelek portalu. Moduł żyje w portal-api (nie w common) świadomie:
 * worker kafelków nie dotyka — jego światem są scripts/tasks.
 */
@Table("tiles")
record Tile(
        @Id Long id,
        String code,
        String name,
        String description,
        String icon,
        String tileType,
        String actionRef,
        boolean active,
        int displayOrder) {
}
