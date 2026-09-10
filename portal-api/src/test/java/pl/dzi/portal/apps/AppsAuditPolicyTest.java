/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.apps;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Polityka audytu /apps (ADR-0007 D3 + aneks 2026-09): co zostawia ślad, a co nie.
 * Test czysto jednostkowy — bez Springa, bez bazy.
 */
class AppsAuditPolicyTest {

    private final AppsAuditPolicy policy = new AppsAuditPolicy();

    private static MockHttpServletRequest get(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    @Test
    void shouldAlwaysWriteDenialsAndErrors() {
        assertThat(policy.shouldWrite(get("/apps/red/app.js"), 403)).isTrue();
        assertThat(policy.shouldWrite(get("/apps/red/logo.png"), 404)).isTrue();
    }

    @Test
    void shouldWriteModuleEntryAndDataFiles() {
        assertThat(policy.shouldWrite(get("/apps/red/"), 200)).isTrue();
        assertThat(policy.shouldWrite(get("/apps/red/index.html"), 200)).isTrue();
        assertThat(policy.shouldWrite(get("/apps/red/dane.csv"), 200)).isTrue();
        assertThat(policy.shouldWrite(get("/apps/red/dane/lista.json"), 200)).isTrue();
        assertThat(policy.shouldWrite(get("/apps/red/raport.pdf"), 200)).isTrue();
    }

    @Test
    void shouldTreatWholeDataDirectoryAsData() {
        // Agregaty ReD: dane w .js — rozszerzenie nie decyduje, katalog data/ tak.
        assertThat(policy.shouldWrite(get("/apps/red/data/ko-01.js"), 200)).isTrue();
        assertThat(policy.shouldWrite(get("/apps/red/data/2026/ko-02.js"), 200)).isTrue();
        assertThat(policy.shouldWrite(get("/apps/red/nested/data/x.bin"), 200)).isTrue();
        assertThat(AppsAuditPolicy.isDataFile("DATA/Plik.JS")).isTrue();
    }

    @Test
    void shouldSkipCompanionAssetsServedCorrectly() {
        assertThat(policy.shouldWrite(get("/apps/red/app.js"), 200)).isFalse();
        assertThat(policy.shouldWrite(get("/apps/red/module.css"), 200)).isFalse();
        assertThat(policy.shouldWrite(get("/apps/red/img/logo.png"), 200)).isFalse();
        assertThat(policy.shouldWrite(get("/apps/red/app.js"), 304)).isFalse();
    }

    @Test
    void shouldNotConfuseModuleCodeNamedDataWithDataDirectory() {
        // Moduł o kodzie "data": jego app.js to nadal zasób towarzyszący.
        assertThat(AppsAuditPolicy.relativeWithinModule("/apps/data/app.js")).isEqualTo("app.js");
        assertThat(policy.shouldWrite(get("/apps/data/app.js"), 200)).isFalse();
        assertThat(policy.shouldWrite(get("/apps/data/data/plik.js"), 200)).isTrue();
    }

    @Test
    void shouldReduceUriToPathWithinModule() {
        assertThat(AppsAuditPolicy.relativeWithinModule("/apps/red/data/ko-01.js")).isEqualTo("data/ko-01.js");
        assertThat(AppsAuditPolicy.relativeWithinModule("/apps/red")).isEqualTo("");
        assertThat(AppsAuditPolicy.relativeWithinModule("/api/tiles")).isEqualTo("/api/tiles");
    }
}
