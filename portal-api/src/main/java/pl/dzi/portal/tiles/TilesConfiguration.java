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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ręczne składanie fasad (wzorzec z JobOffers) — fasady to zwykłe klasy bez adnotacji,
 * co widać też w testach: new TilesFacade(inMemoryRepo, ...) bez kontekstu Springa.
 */
@Configuration
public class TilesConfiguration {

    @Bean
    TilesFacade tilesFacade(TileRepository tiles, TilePermissionRepository tilePermissions) {
        return new TilesFacade(tiles, tilePermissions);
    }

    /** Nazwa "access" jest częścią kontraktu: @PreAuthorize("@access.can...(...)"). */
    @Bean(name = "access")
    AccessFacade accessFacade(TilePermissionRepository tilePermissions) {
        return new AccessFacade(tilePermissions);
    }
}
