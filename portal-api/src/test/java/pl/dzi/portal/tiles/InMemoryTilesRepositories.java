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

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * In-memory double'y obu repozytoriów (wzorzec z JobOffers: fałszywki zamiast mocków).
 * Odwzorowują semantykę SQL z interfejsów: join z tiles, filtr active,
 * porównanie grup lowercase, ORDER BY display_order.
 *
 * Public od Etapu 4: fixture współdzielony z testami modułu tasks (ten sam zestaw uprawnień
 * napędza AccessFacade w teście zlecania zadań).
 */
public final class InMemoryTilesRepositories {

    private InMemoryTilesRepositories() {
    }

    public static final class InMemoryTileRepository implements TileRepository {
        private final List<Tile> tiles;
        private final List<TilePermission> permissions;

        public InMemoryTileRepository(List<Tile> tiles, List<TilePermission> permissions) {
            this.tiles = tiles;
            this.permissions = permissions;
        }

        @Override
        public List<Tile> findVisibleForGroups(Collection<String> groups) {
            return tiles.stream()
                    .filter(Tile::active)
                    .filter(tile -> permissions.stream().anyMatch(permission ->
                            permission.tileId().equals(tile.id())
                                    && groups.contains(permission.adGroup().toLowerCase(Locale.ROOT))))
                    .sorted(Comparator.comparingInt(Tile::displayOrder).thenComparing(Tile::name))
                    .toList();
        }

        @Override
        public Optional<Tile> findByCode(String code) {
            return tiles.stream().filter(tile -> tile.code().equals(code)).findFirst();
        }
    }

    public static final class InMemoryTilePermissionRepository implements TilePermissionRepository {
        private final List<Tile> tiles;
        private final List<TilePermission> permissions;

        public InMemoryTilePermissionRepository(List<Tile> tiles, List<TilePermission> permissions) {
            this.tiles = tiles;
            this.permissions = permissions;
        }

        @Override
        public List<TilePermission> findForGroups(Collection<String> groups) {
            return permissions.stream()
                    .filter(permission -> groups.contains(permission.adGroup().toLowerCase(Locale.ROOT)))
                    .toList();
        }

        @Override
        public List<TilePermission> findForTileAndGroups(String code, Collection<String> groups) {
            Optional<Tile> tile = tiles.stream()
                    .filter(candidate -> candidate.code().equals(code) && candidate.active())
                    .findFirst();
            if (tile.isEmpty()) {
                return List.of();
            }
            return permissions.stream()
                    .filter(permission -> permission.tileId().equals(tile.get().id()))
                    .filter(permission -> groups.contains(permission.adGroup().toLowerCase(Locale.ROOT)))
                    .toList();
        }
    }

    /** Wspólny zestaw danych testowych — ten sam układ co seed dev (V100). */
    public static List<Tile> sampleTiles() {
        return List.of(
                new Tile(1L, "etl-restart", "Restart ETL", "opis", "refresh", "SCRIPT", "etl-restart", true, 10),
                new Tile(2L, "raport-licencje", "Raport licencji", "opis", "chart", "REPORT", "licencje", true, 20),
                new Tile(3L, "admin-tylko", "Panel administratora", "opis", "settings", "LINK", "https://x", true, 30),
                new Tile(4L, "wylaczony", "Nieaktywny", "opis", null, "LINK", "https://x", false, 40));
    }

    public static List<TilePermission> samplePermissions() {
        return List.of(
                new TilePermission(1L, 1L, "DZI-Portal-Admin", PermissionLevel.EXECUTE),
                new TilePermission(2L, 1L, "DZI-Portal-Raporty-Odczyt", PermissionLevel.READ),
                new TilePermission(3L, 2L, "DZI-Portal-Raporty-Odczyt", PermissionLevel.READ),
                new TilePermission(4L, 2L, "DZI-Portal-Admin", PermissionLevel.READ),
                new TilePermission(5L, 3L, "DZI-Portal-Admin", PermissionLevel.READ),
                new TilePermission(6L, 4L, "DZI-Portal-Admin", PermissionLevel.READ),
                // Etap 5: EDIT na kafelku raportu = edycja komórek i import zbioru 'licencje'
                new TilePermission(7L, 2L, "DZI-Portal-Admin", PermissionLevel.EDIT));
    }
}
