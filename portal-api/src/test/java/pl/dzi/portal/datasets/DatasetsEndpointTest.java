/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.datasets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import pl.dzi.portal.infrastructure.audit.AuditContext;
import pl.dzi.portal.infrastructure.security.AdGroupResolver;
import pl.dzi.portal.infrastructure.security.SecurityConfig;
import pl.dzi.portal.tiles.InMemoryTilesRepositories;
import pl.dzi.portal.tiles.TilesConfiguration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static pl.dzi.portal.testsupport.TestRequests.asUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pełny łańcuch: security -> kontroler -> fasada -> AccessFacade (uprawnienia przez kafelek).
 * Wersjonowanie i 422 z raportem walidacji widoczne z perspektywy klienta HTTP.
 */
@WebMvcTest(controllers = DatasetsController.class)
@Import({SecurityConfig.class, TilesConfiguration.class, DatasetsConfiguration.class,
        AuditContext.class, DatasetsEndpointTest.StubBeans.class})
class DatasetsEndpointTest {

    private static final Instant NOW = Instant.parse("2026-07-07T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryDatasetRepositories.InMemoryDatasetRowRepository rows;

    @BeforeEach
    void freshRows() {
        // Izolacja (FIRST): edycje i importy z jednej metody nie mogą przeciekać do kolejnych
        // (wcześniej test ETag i test konfliktu wersji dzieliły ten sam wiersz — commit 31).
        rows.reset();
        java.util.Map<String, String> seed = new java.util.HashMap<>();
        seed.put("produkt", "Acrobat Pro");
        seed.put("posiadane", "40");
        seed.put("uzyte", "33");
        seed.put("uwagi", null);
        rows.save(DatasetRow.create(1L, "Acrobat Pro", seed, "seed", NOW));
    }

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
        pl.dzi.portal.tiles.TileRepository tileRepository() {
            return new InMemoryTilesRepositories.InMemoryTileRepository(
                    InMemoryTilesRepositories.sampleTiles(), InMemoryTilesRepositories.samplePermissions());
        }

        @Bean
        pl.dzi.portal.tiles.TilePermissionRepository tilePermissionRepository() {
            return new InMemoryTilesRepositories.InMemoryTilePermissionRepository(
                    InMemoryTilesRepositories.sampleTiles(), InMemoryTilesRepositories.samplePermissions());
        }

        @Bean
        DatasetRepository datasetRepository() {
            return new InMemoryDatasetRepositories.InMemoryDatasetRepository(
                    InMemoryDatasetRepositories.sampleDataset(), "raport-licencje");
        }

        @Bean
        DatasetColumnRepository datasetColumnRepository() {
            return new InMemoryDatasetRepositories.InMemoryDatasetColumnRepository(
                    InMemoryDatasetRepositories.sampleColumns());
        }

        @Bean
        InMemoryDatasetRepositories.InMemoryDatasetRowRepository datasetRowRepository() {
            return new InMemoryDatasetRepositories.InMemoryDatasetRowRepository(); // seed w @BeforeEach
        }

        @Bean
        DatasetImportRepository datasetImportRepository() {
            return new InMemoryDatasetRepositories.InMemoryDatasetImportRepository();
        }

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Test
    void should_return_view_with_can_edit_false_for_viewer() throws Exception {
        mockMvc.perform(asUser(get("/api/datasets/licencje"), "viewer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.rows[0].data.produkt").value("Acrobat Pro"))
                .andExpect(jsonPath("$.rows[0].version").value(0));
    }

    @Test
    void should_hard_block_edit_without_edit_permission() throws Exception {
        mockMvc.perform(asUser(patch("/api/datasets/licencje/rows/1"), "viewer")
                        .contentType("application/json")
                        .content("{\"columnCode\":\"posiadane\",\"value\":\"41\",\"version\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_edit_cell_and_report_conflict_on_stale_version() throws Exception {
        mockMvc.perform(asUser(patch("/api/datasets/licencje/rows/1"), "admin")
                        .contentType("application/json")
                        .content("{\"columnCode\":\"posiadane\",\"value\":\"1 200,5\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("1200.5"))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(asUser(patch("/api/datasets/licencje/rows/1"), "admin")
                        .contentType("application/json")
                        .content("{\"columnCode\":\"posiadane\",\"value\":\"42\",\"version\":0}"))
                .andExpect(status().isConflict());
    }

    @Test
    void should_import_multipart_file_and_return_merge_report() throws Exception {
        byte[] xlsx = TestWorkbooks.xlsx(new String[]{"Produkt", "Posiadane", "Użyte", "Uwagi"},
                new Object[]{"Acrobat Pro", 40, 33, null},   // bez zmian
                new Object[]{"Visio Plan 2", 15, 9, null})   // nowy
                .readAllBytes();

        mockMvc.perform(asUser(multipart("/api/datasets/licencje/import")
                        .file(new MockMultipartFile("file", "import.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx)), "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.inserted").value(1))
                .andExpect(jsonPath("$.unchanged").value(1));
    }

    @Test
    void should_return_422_with_report_for_invalid_file() throws Exception {
        byte[] xlsx = TestWorkbooks.xlsx(new String[]{"Produkt", "Posiadane", "Użyte"},
                new Object[]{"Produkt A", "abc", 1})
                .readAllBytes();

        mockMvc.perform(asUser(multipart("/api/datasets/licencje/import")
                        .file(new MockMultipartFile("file", "zly.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx)), "admin"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.errors.length()").value(1));
    }

    @Test
    void should_return_400_for_malformed_json_in_edit() throws Exception {
        // HttpMessageNotReadable: wcześniej catch-all zamieniał to w 500 (commit 32)
        mockMvc.perform(asUser(patch("/api/datasets/licencje/rows/1"), "admin")
                        .contentType("application/json")
                        .content("{to nie jest json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_serve_etag_and_304_until_data_changes() throws Exception {
        String etag = mockMvc.perform(asUser(get("/api/datasets/licencje"), "viewer"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");

        mockMvc.perform(asUser(get("/api/datasets/licencje"), "viewer")
                        .header("If-None-Match", etag))
                .andExpect(status().isNotModified());

        // zmiana danych uniewaznia token
        mockMvc.perform(asUser(patch("/api/datasets/licencje/rows/1"), "admin")
                        .contentType("application/json")
                        .content("{\"columnCode\":\"uwagi\",\"value\":\"po zmianie\",\"version\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(asUser(get("/api/datasets/licencje"), "viewer")
                        .header("If-None-Match", etag))
                .andExpect(status().isOk());
    }

}
