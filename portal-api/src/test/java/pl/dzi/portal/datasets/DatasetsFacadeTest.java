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

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import pl.dzi.portal.infrastructure.security.PortalUser;
import pl.dzi.portal.tiles.AccessFacade;
import pl.dzi.portal.tiles.InMemoryTilesRepositories;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pl.dzi.portal.datasets.TestWorkbooks.xlsx;

/**
 * Fasada na double'ach + PRAWDZIWY AccessFacade nad fixture uprawnień tiles —
 * ten sam łańcuch decyzji co na produkcji (zbiór -> kafelek -> grupy).
 */
class DatasetsFacadeTest {

    private static final String[] HEADER = {"Produkt", "Posiadane", "Użyte", "Uwagi"};
    private static final Instant NOW = Instant.parse("2026-07-07T10:00:00Z");

    private final InMemoryDatasetRepositories.InMemoryDatasetRowRepository rows =
            new InMemoryDatasetRepositories.InMemoryDatasetRowRepository();
    private final InMemoryDatasetRepositories.InMemoryDatasetImportRepository imports =
            new InMemoryDatasetRepositories.InMemoryDatasetImportRepository();
    private final DatasetsFacade facade = new DatasetsFacade(
            new InMemoryDatasetRepositories.InMemoryDatasetRepository(
                    InMemoryDatasetRepositories.sampleDataset(), "raport-licencje"),
            new InMemoryDatasetRepositories.InMemoryDatasetColumnRepository(
                    InMemoryDatasetRepositories.sampleColumns()),
            rows,
            imports,
            new AccessFacade(new InMemoryTilesRepositories.InMemoryTilePermissionRepository(
                    InMemoryTilesRepositories.sampleTiles(), InMemoryTilesRepositories.samplePermissions())),
            new XlsxParser(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void should_merge_import_with_insert_update_unchanged_and_missing_counts() throws IOException {
        // given: dwa wiersze w bazie
        rows.save(DatasetRow.create(1L, "Microsoft 365 E3",
                values("Microsoft 365 E3", "1200", "1085", null), "seed", NOW));
        rows.save(DatasetRow.create(1L, "Stary wpis",
                values("Stary wpis", "1", "1", null), "seed", NOW));

        // when: plik aktualizuje pierwszy, nie zna drugiego, dodaje trzeci
        var report = facade.importFile("licencje", "import.xlsx", xlsx(HEADER,
                        new Object[]{"Microsoft 365 E3", 1200, 1140, "po inwentaryzacji"},
                        new Object[]{"Acrobat Pro", 40, 33, null}),
                admin());

        // then
        assertThat(report.status()).isEqualTo("OK");
        assertThat(report.inserted()).isEqualTo(1);
        assertThat(report.updated()).isEqualTo(1);
        assertThat(report.unchanged()).isZero();
        assertThat(report.missingInFile()).isEqualTo(1);
        assertThat(rows.findByDatasetIdAndBusinessKey(1L, "Microsoft 365 E3").orElseThrow()
                .cellValue("uzyte")).contains("1140");
        assertThat(imports.saved).hasSize(1);
        assertThat(imports.saved.get(0).status()).isEqualTo("OK");
    }

    @Test
    void should_count_unchanged_rows_on_reimport_of_same_file() throws IOException {
        facade.importFile("licencje", "a.xlsx", xlsx(HEADER,
                new Object[]{"Acrobat Pro", 40, 33, null}), admin());

        var second = facade.importFile("licencje", "a.xlsx", xlsx(HEADER,
                new Object[]{"Acrobat Pro", 40, 33, null}), admin());

        assertThat(second.unchanged()).isEqualTo(1);
        assertThat(second.inserted()).isZero();
        assertThat(second.updated()).isZero();
    }

    @Test
    void should_reject_whole_import_on_any_validation_error_and_persist_report() throws IOException {
        var report = facade.importFile("licencje", "zly.xlsx", xlsx(HEADER,
                        new Object[]{"Produkt A", "abc", 1, null}),
                admin());

        assertThat(report.status()).isEqualTo("REJECTED");
        assertThat(rows.findByDatasetIdOrderByBusinessKey(1L)).as("nic nie zapisano").isEmpty();
        assertThat(imports.saved.get(0).status()).isEqualTo("REJECTED");
        assertThat(imports.saved.get(0).errorReport()).contains("nie jest liczbą");
    }

    @Test
    void should_edit_cell_with_canonicalization_and_version_bump() {
        DatasetRow saved = rows.save(DatasetRow.create(1L, "Acrobat Pro",
                values("Acrobat Pro", "40", "33", null), "seed", NOW));

        var edited = facade.editCell("licencje", saved.id(), "posiadane", "1 200,5", 0, admin());

        assertThat(edited.value()).isEqualTo("1200.5");
        assertThat(edited.version()).isEqualTo(1);
        assertThat(rows.findById(saved.id()).orElseThrow().updatedBy()).isEqualTo("admin");
    }

    @Test
    void should_return_conflict_for_stale_version() {
        DatasetRow saved = rows.save(DatasetRow.create(1L, "Acrobat Pro",
                values("Acrobat Pro", "40", "33", null), "seed", NOW));
        facade.editCell("licencje", saved.id(), "posiadane", "41", 0, admin());

        assertThatThrownBy(() -> facade.editCell("licencje", saved.id(), "posiadane", "42", 0, admin()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void should_reject_edit_of_non_editable_and_unknown_column() {
        DatasetRow saved = rows.save(DatasetRow.create(1L, "Acrobat Pro",
                values("Acrobat Pro", "40", "33", null), "seed", NOW));

        assertThatThrownBy(() -> facade.editCell("licencje", saved.id(), "produkt", "X", 0, admin()))
                .hasMessageContaining("nie podlega edycji");
        assertThatThrownBy(() -> facade.editCell("licencje", saved.id(), "widmo", "X", 0, admin()))
                .hasMessageContaining("Nieznana kolumna");
    }

    @Test
    void should_expose_can_edit_flag_and_deny_users_without_read() {
        assertThat(facade.view("licencje", admin()).canEdit()).isTrue();
        assertThat(facade.view("licencje", viewer()).canEdit()).isFalse();
        assertThatThrownBy(() -> facade.view("licencje", nobody()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> facade.importFile("licencje", "x.xlsx",
                xlsx(HEADER, new Object[]{"A", 1, 1, null}), viewer()))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static Map<String, String> values(String produkt, String posiadane, String uzyte, String uwagi) {
        Map<String, String> map = new java.util.HashMap<>();
        map.put("produkt", produkt);
        map.put("posiadane", posiadane);
        map.put("uzyte", uzyte);
        map.put("uwagi", uwagi);
        return map;
    }

    private static Authentication admin() {
        return auth("admin", "DZI-Portal-Admin");
    }

    private static Authentication viewer() {
        return auth("viewer", "DZI-Portal-Raporty-Odczyt");
    }

    private static Authentication nobody() {
        return auth("nobody");
    }

    private static Authentication auth(String login, String... groups) {
        return new TestingAuthenticationToken(new PortalUser(login, Set.of(groups)), "N/A");
    }
}
