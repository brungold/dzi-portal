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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Konfiguracja wyłącznie dla środowisk bez prod.
 */
@Configuration
@Profile("!prod")
class DevSecurityConfiguration {

    /**
     * Poza prod grupy pochodzą z konfiguracji (portal.security.static-groups) —
     * zero zależności od AD na maszynie deweloperskiej.
     * Osobna klasa (nie bean w SecurityConfig), żeby testy plasterkowe mogły
     * podstawić własny AdGroupResolver bez konfliktu beanów.
     */
    @Bean
    AdGroupResolver adGroupResolver(PortalSecurityProperties properties) {
        return new StaticAdGroupResolver(properties.staticGroups());
    }

    /**
     * Dev-only: Spring serwuje statyczny frontend (application-dev.yml wskazuje
     * katalog ../frontend), w prod robi to IIS. Order(0) — przed łańcuchem API (1);
     * wzorce nie zachodzą na /api/**, a prod-owe deny-all (Order 2) zostaje nietknięte,
     * bo ten bean w prod nie istnieje.
     *
     * UWAGA (lekcja commitu 37): "/*.css" pasuje TYLKO do plikow na najwyzszym
     * poziomie — podkatalogi wymagaja wlasnych wzorcow (/css/**, /js/**, /apps/**).
     * Bez nich zasoby wpadaja do deny-all i przegladarka dostaje 403.
     */
    @Bean
    @Order(0)
    SecurityFilterChain devStaticFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/", "/*.html", "/*.js", "/*.css", "/favicon.ico",
                        "/css/**", "/js/**", "/lib/**", "/img/**", "/assets/**", "/apps/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
