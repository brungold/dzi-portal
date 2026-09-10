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
import org.springframework.web.server.ResponseStatusException;
import pl.dzi.portal.admin.AdminViews.PrincipalKind;
import pl.dzi.portal.admin.AdminViews.StatsView;
import pl.dzi.portal.admin.AdminViews.TileDetail;
import pl.dzi.portal.admin.AdminViews.TileOverview;
import pl.dzi.portal.admin.AdminViews.UserView;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Logika składania widoków — bez Springa, bez bazy (dane: InMemoryAdminQueries.sample()). */
class AdminFacadeTest {

    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");
    private final AdminFacade facade = new AdminFacade(InMemoryAdminQueries.sample(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void shouldListAllTilesIncludingInactiveWithCountsAndWindows() {
        List<TileOverview> tiles = facade.tilesOverview();

        assertThat(tiles).extracting(TileOverview::code)
                .containsExactly("red-pisma-sprawy", "epo-podpis", "wylaczony", "administracja");
        TileOverview red = tiles.get(0);
        assertThat(red.permissionCount()).isEqualTo(3);
        assertThat(red.visits30()).isEqualTo(3);
        assertThat(red.lastVisit()).isEqualTo(Instant.parse("2026-09-08T10:00:00Z"));
        TileOverview epo = tiles.get(1);
        assertThat(epo.visits30()).isZero();          // wejście z czerwca jest poza oknem 30 dni...
        assertThat(epo.visits90()).isZero();          // ...i poza oknem 90 dni (1.06 < 11.06)
        assertThat(epo.lastVisit()).isEqualTo(Instant.parse("2026-06-01T10:00:00Z")); // ale ostatnie wejście jest znane
        assertThat(tiles.get(2).active()).isFalse();
    }

    @Test
    void shouldClassifyPrincipalsAndFlagLoginsUnknownToAudit() {
        TileDetail red = facade.tileDetail("red-pisma-sprawy");

        assertThat(red.permissions()).hasSize(3);
        assertThat(red.permissions().get(0).kind()).isEqualTo(PrincipalKind.LOGIN);
        assertThat(red.permissions().get(0).knownInAudit()).isTrue();
        assertThat(red.permissions().get(2).principal()).isEqualTo("literowka.login");
        assertThat(red.permissions().get(2).knownInAudit()).isFalse();   // nigdy nie wszedł = podejrzenie literówki
        assertThat(red.visitors()).extracting(v -> v.login()).containsExactly("jan.kowalski", "kowalska.anna2");
        assertThat(red.visitors().get(0).visits()).isEqualTo(2);

        TileDetail epo = facade.tileDetail("epo-podpis");
        assertThat(epo.permissions()).extracting(p -> p.kind())
                .containsExactly(PrincipalKind.DEPARTMENT, PrincipalKind.ALL);
        assertThat(epo.permissions()).allMatch(p -> p.knownInAudit()); // flaga dotyczy tylko loginów
    }

    @Test
    void shouldRejectUnknownTileWith404AndBadCodeWith400() {
        assertThatThrownBy(() -> facade.tileDetail("nie-ma"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        assertThatThrownBy(() -> facade.tileDetail("../x"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void shouldShowWhatPersonSeesWithDepartmentTilesAsConditional() {
        UserView jan = facade.userView(" Jan.Kowalski ");

        assertThat(jan.login()).isEqualTo("jan.kowalski");
        assertThat(jan.knownInAudit()).isTrue();
        assertThat(jan.byLogin()).extracting(g -> g.code()).containsExactly("red-pisma-sprawy");
        assertThat(jan.forAll()).extracting(g -> g.code()).containsExactly("epo-podpis");
        assertThat(jan.byDepartment()).singleElement().satisfies(g -> {
            assertThat(g.code()).isEqualTo("epo-podpis");
            assertThat(g.via()).isEqualTo("dzi");
        });
        assertThat(jan.visits()).singleElement().satisfies(v -> {
            assertThat(v.code()).isEqualTo("red-pisma-sprawy");
            assertThat(v.name()).isEqualTo("ReD Dokumenty i Sprawy");
            assertThat(v.visits()).isEqualTo(2);
        });

        UserView nobody = facade.userView("nikt.taki");
        assertThat(nobody.knownInAudit()).isFalse();
        assertThat(nobody.firstSeen()).isNull();
    }

    @Test
    void shouldComputeStatsForDefaultWindowAndRejectInvertedRange() {
        StatsView stats = facade.stats(null, null);

        assertThat(stats.from()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(stats.to()).isEqualTo(LocalDate.of(2026, 9, 9));
        assertThat(stats.portalVisits()).isEqualTo(1);
        assertThat(stats.perTile()).singleElement().satisfies(t -> {
            assertThat(t.code()).isEqualTo("red-pisma-sprawy");
            assertThat(t.visits()).isEqualTo(3);
            assertThat(t.distinctUsers()).isEqualTo(2);
        });
        assertThat(stats.perUser().get(0).login()).isEqualTo("jan.kowalski");

        assertThatThrownBy(() -> facade.stats(LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void shouldClampAuditLimitAndNormalizeFilters() {
        assertThat(facade.recentAudit(null, null, null, null)).hasSize(6);
        assertThat(facade.recentAudit(2, null, null, null)).hasSize(2);
        assertThat(facade.recentAudit(0, null, null, null)).hasSize(1);
        assertThat(facade.recentAudit(10, null, "app_denied", null)).singleElement()
                .satisfies(row -> assertThat(row.httpStatus()).isEqualTo(403));
        assertThat(facade.recentAudit(10, "Jan.Kowalski", null, "red-pisma-sprawy")).hasSize(2);
    }

    @Test
    void shouldClassifyPrincipalByShape() {
        assertThat(AdminFacade.kindOf("wszyscy")).isEqualTo(PrincipalKind.ALL);
        assertThat(AdminFacade.kindOf("Wszyscy")).isEqualTo(PrincipalKind.ALL);
        assertThat(AdminFacade.kindOf("kowalski.jan")).isEqualTo(PrincipalKind.LOGIN);
        assertThat(AdminFacade.kindOf("posnik.robert2")).isEqualTo(PrincipalKind.LOGIN);
        assertThat(AdminFacade.kindOf("dzi")).isEqualTo(PrincipalKind.DEPARTMENT);
    }
}
