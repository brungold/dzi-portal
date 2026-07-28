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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pl.dzi.portal.infrastructure.audit.AuditContext;
import pl.dzi.portal.infrastructure.security.SecurityConfig;

import java.util.Set;

import static pl.dzi.portal.testsupport.TestRequests.asUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test plasterkowy dwustronnego egzekwowania RBAC:
 *  - lista filtruje po grupach (poziom miękki),
 *  - szczegóły bez uprawnień => 403 mimo znajomości kodu (poziom twardy, @PreAuthorize),
 *  - kod nieistniejący => też 403 (brak uprawnień nie zdradza istnienia zasobu).
 * Persony przełącza nagłówek: stub resolvera mapuje login -> grupy.
 */
@WebMvcTest(controllers = TilesController.class)
@Import({SecurityConfig.class, TilesConfiguration.class, AuditContext.class, TilesEndpointTest.StubBeans.class})
class TilesEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class StubBeans {

        @Bean
        pl.dzi.portal.infrastructure.security.AdGroupResolver adGroupResolver() {
            return samAccountName -> switch (samAccountName) {
                case "admin" -> Set.of("DZI-Portal-Admin");
                case "viewer" -> Set.of("DZI-Portal-Raporty-Odczyt");
                default -> Set.of();
            };
        }

        @Bean
        TileRepository tileRepository() {
            return new InMemoryTilesRepositories.InMemoryTileRepository(
                    InMemoryTilesRepositories.sampleTiles(), InMemoryTilesRepositories.samplePermissions());
        }

        @Bean
        TilePermissionRepository tilePermissionRepository() {
            return new InMemoryTilesRepositories.InMemoryTilePermissionRepository(
                    InMemoryTilesRepositories.sampleTiles(), InMemoryTilesRepositories.samplePermissions());
        }
    }

    @Test
    void should_return_401_without_identity() throws Exception {
        mockMvc.perform(get("/api/tiles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_filter_list_by_groups_and_expose_capability_flags() throws Exception {
        mockMvc.perform(asUser(get("/api/tiles"), "viewer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("etl-restart"))
                .andExpect(jsonPath("$[0].canExecute").value(false))
                .andExpect(jsonPath("$[1].code").value("raport-licencje"));
    }

    @Test
    void should_return_tile_details_when_read_permission_present() throws Exception {
        mockMvc.perform(asUser(get("/api/tiles/admin-tylko"), "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("admin-tylko"));
    }

    @Test
    void should_hard_block_details_without_permission_even_with_known_code() throws Exception {
        // viewer NIE widzi kafelka na liście, ale zna kod i skleja request ręcznie
        mockMvc.perform(asUser(get("/api/tiles/admin-tylko"), "viewer"))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_return_403_for_unknown_code_without_leaking_existence() throws Exception {
        mockMvc.perform(asUser(get("/api/tiles/nie-istnieje"), "admin"))
                .andExpect(status().isForbidden());
    }

}
