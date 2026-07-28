/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.tasks;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pl.dzi.portal.common.script.Script;

import java.util.Optional;

/**
 * Widok whitelisty oczami zlecania: kafelek -> skrypt. Celowo własne zapytanie
 * zamiast sięgania do wnętrza modułu tiles — moduły spina wyłącznie schemat bazy.
 */
interface RunnableScriptRepository extends Repository<Script, Long> {

    /** Aktywny skrypt aktywnego kafelka typu SCRIPT — wszystkie warunki w jednym miejscu. */
    @Query("""
            SELECT s.*
            FROM scripts s
            JOIN tiles t ON t.action_ref = s.code
            WHERE t.code = :tileCode
              AND t.tile_type = 'SCRIPT'
              AND t.active = 1
              AND s.active = 1
            """)
    Optional<Script> findActiveScriptForTile(@Param("tileCode") String tileCode);

    @Query("SELECT id FROM tiles WHERE code = :tileCode")
    Optional<Long> findTileId(@Param("tileCode") String tileCode);
}
