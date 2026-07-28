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

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pl.dzi.portal.infrastructure.security.PortalUser;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Widok kafelków oczami konkretnego użytkownika (poziom "miękki" RBAC — patrz AccessFacade).
 * Wzorzec z JobOffers: mała fasada per obszar zamiast jednej fasady-boga.
 */
@RequiredArgsConstructor
class TilesFacade {

    private final TileRepository tiles;
    private final TilePermissionRepository tilePermissions;

    List<TileResponse> visibleTiles(PortalUser user) {
        if (user.groups().isEmpty()) {
            return List.of();
        }
        var groups = AccessFacade.lowercased(user.groups());
        Map<Long, Set<PermissionLevel>> levelsByTileId = tilePermissions.findForGroups(groups).stream()
                .collect(Collectors.groupingBy(TilePermission::tileId,
                        Collectors.mapping(TilePermission::permissionLevel, Collectors.toSet())));

        return tiles.findVisibleForGroups(groups).stream()
                .map(tile -> toResponse(tile, levelsByTileId.getOrDefault(tile.id(), Set.of())))
                .toList();
    }

    /**
     * Szczegóły kafelka. Wywoływane WYŁĄCZNIE zza @PreAuthorize(canRead) — 404 poniżej
     * to defensywa na wyścig (dezaktywacja między kontrolą a odczytem), nie ścieżka biznesowa.
     */
    TileResponse tile(String code, PortalUser user) {
        Tile tile = tiles.findByCode(code)
                .filter(Tile::active)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kafelek nie istnieje"));
        Set<PermissionLevel> levels = tilePermissions
                .findForTileAndGroups(code, AccessFacade.lowercased(user.groups())).stream()
                .map(TilePermission::permissionLevel)
                .collect(Collectors.toSet());
        return toResponse(tile, levels);
    }

    private static TileResponse toResponse(Tile tile, Set<PermissionLevel> levels) {
        boolean canExecute = levels.stream().anyMatch(level -> level.covers(PermissionLevel.EXECUTE));
        boolean canEdit = levels.stream().anyMatch(level -> level.covers(PermissionLevel.EDIT));
        return new TileResponse(tile.code(), tile.name(), tile.description(), tile.icon(),
                tile.tileType(), tile.actionRef(), canExecute, canEdit);
    }
}
