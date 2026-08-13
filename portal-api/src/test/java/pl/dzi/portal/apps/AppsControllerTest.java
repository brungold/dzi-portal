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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.dzi.portal.tiles.AccessFacade;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kontroler strażnika w izolacji (standalone MockMvc, bez kontekstu i łańcucha security —
 * uwierzytelnianie /apps/** pokrywa konfiguracja łańcuchów, tu testujemy decyzje kontrolera).
 * AccessFacade podstawiona podklasą z jednym przełącznikiem — kontrakt canRead bez bazy.
 */
class AppsControllerTest {

    @TempDir
    Path baseDir;

    private boolean readAllowed = true;
    private MockMvc mockMvc;

    private final AccessFacade access = new AccessFacade(null) {
        @Override
        public boolean canRead(String tileCode, Authentication authentication) {
            return readAllowed;
        }
    };

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(baseDir.resolve("red-pisma-sprawy"));
        Files.writeString(baseDir.resolve("red-pisma-sprawy/index.html"),
                "<!doctype html><title>ReD</title>", StandardCharsets.UTF_8);
        Files.writeString(baseDir.resolve("red-pisma-sprawy/dane.csv"),
                "kodjo;ko\nCEN;BOiS\n", StandardCharsets.UTF_8);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AppsController(new AppsProperties(baseDir), access)).build();
    }

    @Test
    void shouldServeIndexHtmlForModuleDirectory() throws Exception {
        mockMvc.perform(get("/apps/red-pisma-sprawy/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/html; charset=utf-8"))
                .andExpect(header().exists("ETag"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ReD")));
    }

    @Test
    void shouldRedirectModuleUrlWithoutTrailingSlash() throws Exception {
        // Względne ścieżki w index.html liczą się od katalogu — bez ukośnika by się rozjechały.
        mockMvc.perform(get("/apps/red-pisma-sprawy"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/apps/red-pisma-sprawy/"));
    }

    @Test
    void shouldServeCsvWithUtf8ContentType() throws Exception {
        mockMvc.perform(get("/apps/red-pisma-sprawy/dane.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=utf-8"));
    }

    @Test
    void shouldReturnForbiddenHtmlPageWhenAccessDenied() throws Exception {
        readAllowed = false;

        mockMvc.perform(get("/apps/red-pisma-sprawy/dane.csv"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Brak dostępu")));
    }

    @Test
    void shouldReturnNotFoundForMissingFileAndUnknownModule() throws Exception {
        mockMvc.perform(get("/apps/red-pisma-sprawy/nie-ma.txt")).andExpect(status().isNotFound());
        mockMvc.perform(get("/apps/nie-istnieje/")).andExpect(status().isNotFound());
        mockMvc.perform(get("/apps/")).andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotModifiedWhenETagMatches() throws Exception {
        String etag = mockMvc.perform(get("/apps/red-pisma-sprawy/dane.csv"))
                .andReturn().getResponse().getHeader("ETag");

        mockMvc.perform(get("/apps/red-pisma-sprawy/dane.csv").header("If-None-Match", etag))
                .andExpect(status().isNotModified());
    }
}
