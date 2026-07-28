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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import pl.dzi.portal.infrastructure.security.PortalUser;
import pl.dzi.portal.tiles.InMemoryTilesRepositories.InMemoryTilePermissionRepository;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AccessFacadeTest {

    private final AccessFacade facade = new AccessFacade(new InMemoryTilePermissionRepository(
            InMemoryTilesRepositories.sampleTiles(), InMemoryTilesRepositories.samplePermissions()));

    @Test
    void should_grant_read_and_execute_from_single_execute_permission() {
        // hierarchia liniowa: EXECUTE pokrywa READ — jedno nadanie wystarcza
        Authentication admin = authWithGroups("DZI-Portal-Admin");

        assertThat(facade.canRead("etl-restart", admin)).isTrue();
        assertThat(facade.canExecute("etl-restart", admin)).isTrue();
        assertThat(facade.canEdit("etl-restart", admin)).isFalse();
    }

    @Test
    void should_not_grant_execute_from_read_permission() {
        Authentication viewer = authWithGroups("DZI-Portal-Raporty-Odczyt");

        assertThat(facade.canRead("etl-restart", viewer)).isTrue();
        assertThat(facade.canExecute("etl-restart", viewer)).isFalse();
    }

    @Test
    void should_compare_group_names_case_insensitively_like_ad_does() {
        Authentication mixedCase = authWithGroups("dzi-portal-ADMIN");

        assertThat(facade.canExecute("etl-restart", mixedCase)).isTrue();
    }

    @Test
    void should_deny_unknown_tile_without_revealing_existence() {
        Authentication admin = authWithGroups("DZI-Portal-Admin");

        assertThat(facade.canRead("nie-istnieje", admin)).isFalse();
    }

    @Test
    void should_deny_inactive_tile() {
        Authentication admin = authWithGroups("DZI-Portal-Admin");

        assertThat(facade.canRead("wylaczony", admin)).isFalse();
    }

    @Test
    void should_deny_user_without_groups_before_touching_repository() {
        Authentication noGroups = authWithGroups();

        assertThat(facade.canRead("etl-restart", noGroups)).isFalse();
    }

    private static Authentication authWithGroups(String... groups) {
        return new TestingAuthenticationToken(new PortalUser("jkowalski", Set.of(groups)), "N/A");
    }
}
