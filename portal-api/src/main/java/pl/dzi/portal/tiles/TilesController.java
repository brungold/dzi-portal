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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import pl.dzi.portal.infrastructure.audit.AuditContext;
import pl.dzi.portal.infrastructure.audit.Audited;
import pl.dzi.portal.infrastructure.security.PortalUser;

import java.util.List;

/**
 * REST kafelków. Lista sama w sobie jest filtrem (miękki RBAC); endpoint szczegółów
 * pokazuje wzorzec twardej blokady, który powielą akcje z Etapu 4/5:
 * @PreAuthorize -> AccessFacade (bean "access") -> 403 + DENIED w audycie.
 * Odmowa z @PreAuthorize pada PRZED ciałem metody, więc objectRef nie zostanie
 * ustawiony — ale action (z @Audited) i path z kodem kafelka są już w audycie.
 */
@RestController
@RequiredArgsConstructor
class TilesController {

    private final TilesFacade tilesFacade;
    private final AuditContext auditContext;

    @Audited(action = "TILES_LIST")
    @GetMapping("/api/tiles")
    List<TileResponse> visibleTiles(Authentication authentication) {
        return tilesFacade.visibleTiles((PortalUser) authentication.getPrincipal());
    }

    @Audited(action = "TILE_READ")
    @PreAuthorize("@access.canRead(#code, authentication)")
    @GetMapping("/api/tiles/{code}")
    TileResponse tile(@PathVariable String code, Authentication authentication) {
        auditContext.setObjectRef("tile:" + code);
        return tilesFacade.tile(code, (PortalUser) authentication.getPrincipal());
    }
}
