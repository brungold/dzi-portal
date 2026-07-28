/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Beany wspólne dla obu procesów (api i worker).
 */
@Configuration
public class CommonConfiguration {

    /**
     * Wstrzykiwany Clock zamiast Instant.now() rozsianego po kodzie — pełna kontrola
     * nad czasem w testach (ten sam wzorzec co w JobOffers).
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
