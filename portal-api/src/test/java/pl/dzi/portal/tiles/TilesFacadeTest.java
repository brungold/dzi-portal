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

import org.junit.jupiter.api.Test;
import pl.dzi.portal.infrastructure.security.PortalUser;
import pl.dzi.portal.tiles.InMemoryTilesRepositories.InMemoryTilePermissionRepository;
import pl.dzi.portal.tiles.InMemoryTilesRepositories.InMemoryTileRepository;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TilesFacadeTest {

    private final TilesFacade facade = new TilesFacade(
            new InMemoryTileRepository(InMemoryTilesRepositories.sampleTiles(), InMemoryTilesRepositories.samplePermissions()),
            new InMemoryTilePermissionRepository(InMemoryTilesRepositories.sampleTiles(), InMemoryTilesRepositories.samplePermissions()));

    @Test
    void should_return_only_visible_tiles_with_capability_flags_for_viewer() {
        var viewer = new PortalUser("viewer", Set.of("DZI-Portal-Raporty-Odczyt"));

        List<TileResponse> tiles = facade.visibleTiles(viewer);

        assertThat(tiles).extracting(TileResponse::code)
                .containsExactly("etl-restart", "raport-licencje"); // bez admin-tylko i nieaktywnego
        assertThat(tiles.get(0).canExecute()).as("READ nie daje uruchamiania").isFalse();
    }

    @Test
    void should_flag_can_execute_for_admin() {
        var admin = new PortalUser("admin", Set.of("DZI-Portal-Admin"));

        List<TileResponse> tiles = facade.visibleTiles(admin);

        assertThat(tiles).extracting(TileResponse::code)
                .containsExactly("etl-restart", "raport-licencje", "admin-tylko");
        assertThat(tiles.get(0).canExecute()).isTrue();
    }

    @Test
    void should_return_empty_list_for_user_without_groups() {
        var nobody = new PortalUser("nobody", Set.of());

        assertThat(facade.visibleTiles(nobody)).isEmpty();
    }
}
