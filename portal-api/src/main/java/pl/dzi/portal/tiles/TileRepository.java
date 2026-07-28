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

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Celowo baza Repository (nie CrudRepository): powierzchnia = dokładnie dwa zapytania,
 * dzięki czemu in-memory double w testach ma ~20 linii zamiast implementacji
 * kilkunastu metod CRUD, których nikt nie woła.
 *
 * UWAGA: parametr :groups NIE może być pustą kolekcją (SQL "IN ()") — fasady
 * gwarantują wcześniejszy zwrot dla użytkownika bez grup.
 */
public interface TileRepository extends Repository<Tile, Long> {

    /** Kafelki widoczne dla zestawu grup (nazwy grup podawane lowercase). */
    @Query("""
            SELECT DISTINCT t.*
            FROM tiles t
            JOIN tile_permissions p ON p.tile_id = t.id
            WHERE t.active = 1
              AND LOWER(p.ad_group) IN (:groups)
            ORDER BY t.display_order, t.name
            """)
    List<Tile> findVisibleForGroups(@Param("groups") Collection<String> groups);

    Optional<Tile> findByCode(String code);
}
