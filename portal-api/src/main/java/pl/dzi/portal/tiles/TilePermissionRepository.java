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

/** Jak TileRepository: minimalna powierzchnia, jawny SQL, zakaz pustych kolekcji w :groups. */
public interface TilePermissionRepository extends Repository<TilePermission, Long> {

    /** Wszystkie nadania dla grup użytkownika — do policzenia flag canExecute/canEdit na liście. */
    @Query("SELECT * FROM tile_permissions WHERE LOWER(ad_group) IN (:groups)")
    List<TilePermission> findForGroups(@Param("groups") Collection<String> groups);

    /** Nadania dla konkretnego (aktywnego) kafelka — twarda kontrola dostępu. */
    @Query("""
            SELECT p.*
            FROM tile_permissions p
            JOIN tiles t ON t.id = p.tile_id
            WHERE t.code = :code
              AND t.active = 1
              AND LOWER(p.ad_group) IN (:groups)
            """)
    List<TilePermission> findForTileAndGroups(@Param("code") String code,
                                              @Param("groups") Collection<String> groups);
}
