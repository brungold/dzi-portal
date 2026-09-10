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

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Bramka 'access' dla testów panelu administracyjnego: repozytoria in-memory z kafelkiem
 * 'administracja' (READ dla grupy DZI-Portal-Admin) i jednym zwykłym kafelkiem.
 * Mieszka w pakiecie tiles, bo Tile/TilePermission są pakietowo-prywatne — testy
 * z pl.dzi.portal.admin importują tę klasę, nie rekordy.
 */
@TestConfiguration
public class AdminGateTestConfiguration {

    private static List<Tile> tiles() {
        return List.of(
                new Tile(1L, "red-pisma-sprawy", "ReD Dokumenty i Sprawy", "opis", null, "LINK", "/apps/red-pisma-sprawy/", true, 10),
                new Tile(9L, "administracja", "Administracja portalu", "opis", "settings", "LINK", "/apps/administracja/", true, 900));
    }

    private static List<TilePermission> permissions() {
        return List.of(
                new TilePermission(1L, 1L, "DZI-Portal-Raporty-Odczyt", PermissionLevel.READ),
                new TilePermission(2L, 9L, "DZI-Portal-Admin", PermissionLevel.READ));
    }

    @Bean
    TileRepository tileRepository() {
        return new InMemoryTilesRepositories.InMemoryTileRepository(tiles(), permissions());
    }

    @Bean
    TilePermissionRepository tilePermissionRepository() {
        return new InMemoryTilesRepositories.InMemoryTilePermissionRepository(tiles(), permissions());
    }
}
