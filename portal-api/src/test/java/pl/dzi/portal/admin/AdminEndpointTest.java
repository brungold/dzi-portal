/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pl.dzi.portal.infrastructure.audit.AuditContext;
import pl.dzi.portal.infrastructure.security.AdGroupResolver;
import pl.dzi.portal.infrastructure.security.SecurityConfig;
import pl.dzi.portal.tiles.AdminGateTestConfiguration;
import pl.dzi.portal.tiles.TilesConfiguration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pl.dzi.portal.testsupport.TestRequests.asUser;

/**
 * Bramka /api/admin/**: tożsamość jest wymagana, a dalej decyduje READ na kafelku
 * 'administracja' (bean 'access' + tile_permissions), dokładnie jak dla każdego kafelka.
 * Kafelek 'administracja' i uprawnienia bramki dostarcza AdminGateTestConfiguration
 * (pakiet tiles — rekordy Tile/TilePermission są pakietowo-prywatne).
 */
@WebMvcTest(controllers = AdminController.class)
@Import({SecurityConfig.class, TilesConfiguration.class, AuditContext.class, AdminConfiguration.class,
        AdminGateTestConfiguration.class, AdminEndpointTest.StubBeans.class})
class AdminEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class StubBeans {

        @Bean
        AdGroupResolver adGroupResolver() {
            return samAccountName -> switch (samAccountName) {
                case "admin" -> Set.of("DZI-Portal-Admin");
                case "viewer" -> Set.of("DZI-Portal-Raporty-Odczyt");
                default -> Set.of();
            };
        }

        @Bean
        AdminQueries adminQueries() {
            return InMemoryAdminQueries.sample();
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Test
    void should_return_401_without_identity() throws Exception {
        mockMvc.perform(get("/api/admin/tiles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return_403_for_authenticated_user_without_admin_tile() throws Exception {
        mockMvc.perform(asUser(get("/api/admin/tiles"), "viewer"))
                .andExpect(status().isForbidden());
        mockMvc.perform(asUser(get("/api/admin/audit"), "viewer"))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_list_all_tiles_for_admin() throws Exception {
        mockMvc.perform(asUser(get("/api/admin/tiles"), "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].code").value("red-pisma-sprawy"))
                .andExpect(jsonPath("$[0].permissionCount").value(3))
                .andExpect(jsonPath("$[0].visits30").value(3))
                .andExpect(jsonPath("$[2].active").value(false));
    }

    @Test
    void should_expose_tile_detail_with_unknown_login_flag() throws Exception {
        mockMvc.perform(asUser(get("/api/admin/tiles/red-pisma-sprawy"), "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(3))
                .andExpect(jsonPath("$.permissions[2].principal").value("literowka.login"))
                .andExpect(jsonPath("$.permissions[2].knownInAudit").value(false))
                .andExpect(jsonPath("$.visitors[0].login").value("jan.kowalski"));
        mockMvc.perform(asUser(get("/api/admin/tiles/nie-ma"), "admin"))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_answer_user_logins_stats_and_audit() throws Exception {
        mockMvc.perform(asUser(get("/api/admin/users/jan.kowalski"), "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.knownInAudit").value(true))
                .andExpect(jsonPath("$.byLogin[0].code").value("red-pisma-sprawy"))
                .andExpect(jsonPath("$.byDepartment[0].via").value("dzi"));
        mockMvc.perform(asUser(get("/api/admin/logins").param("q", "kowal"), "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(asUser(get("/api/admin/stats").param("from", "2026-09-01").param("to", "2026-09-09"), "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perTile[0].visits").value(3))
                .andExpect(jsonPath("$.portalVisits").value(1));
        mockMvc.perform(asUser(get("/api/admin/audit").param("limit", "2").param("action", "APP_OPEN"), "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].action").value("APP_OPEN"));
    }

    @Test
    void should_reject_bad_input_with_400() throws Exception {
        mockMvc.perform(asUser(get("/api/admin/users/{login}", "jan kowalski"), "admin"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asUser(get("/api/admin/stats").param("from", "2026-09-09").param("to", "2026-09-01"), "admin"))
                .andExpect(status().isBadRequest());
    }
}
