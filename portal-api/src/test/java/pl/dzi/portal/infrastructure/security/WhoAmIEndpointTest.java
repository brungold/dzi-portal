/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test plasterkowy pełnego łańcucha security dla /api/whoami.
 * To jest luksus wariantu A: nagłówek testuje się zwykłym MockMvc,
 * czego z prawdziwym handshakiem Kerberosa nie da się zrobić.
 *
 * Uwaga Boot 4: jeśli import WebMvcTest nie kompiluje się po aktualizacji,
 * usuń linię importu i użyj auto-importu IDE (moduły przeniosły część pakietów).
 */
@WebMvcTest(controllers = WhoAmIController.class)
@Import({SecurityConfig.class, WhoAmIEndpointTest.StubBeans.class})
class WhoAmIEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class StubBeans {
        @Bean
        AdGroupResolver adGroupResolver() {
            return samAccountName -> Set.of("DZI-Portal-Admin", "DZI-Portal-Raporty-Odczyt");
        }
    }

    @Test
    void should_return_401_when_identity_header_is_absent() throws Exception {
        mockMvc.perform(get("/api/whoami"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return_identity_and_groups_when_header_comes_from_loopback() throws Exception {
        mockMvc.perform(get("/api/whoami")
                        .header("X-Auth-User", "DZI\\jkowalski")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("jkowalski"))
                .andExpect(jsonPath("$.groups.length()").value(2));
    }

    @Test
    void should_reject_spoofed_header_sent_from_remote_host() throws Exception {
        mockMvc.perform(get("/api/whoami")
                        .header("X-Auth-User", "DZI\\jkowalski")
                        .with(request -> {
                            request.setRemoteAddr("10.1.2.3");
                            return request;
                        }))
                .andExpect(status().isUnauthorized());
    }
}
