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

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import pl.dzi.portal.apps.AppsAuthenticationEntryPoint;

/**
 * UWAGA (2026-09): łańcuchy {@code /api} i {@code /apps} zdefiniowane tutaj to WARIANT A
 * (LoopbackHeaderAuthenticationFilter + grupy AD). Pod profilem {@code declared} są tworzone,
 * ale PRZESŁONIĘTE łańcuchami z DeclaredSecurityConfiguration (@Order -10/-9). Beany
 * wspólne (entry point, fallback) obowiązują w obu wariantach. Ogrodzenie profilem
 * wymaga jednoczesnej zmiany DevSecurityConfiguration i przebiegu testów — zaplanowane.
 *
 * Konfiguracja security — jawna i w całości pod kontrolą (bez form-loginu, basic auth i sesji).
 * Model: IIS wykonuje Kerberos, aplikacja ufa nagłówkowi wyłącznie z loopbacku.
 *
 * Public świadomie (od Etapu 3): testy plasterkowe modułów biznesowych robią
 * @Import(SecurityConfig.class), żeby dwustronne egzekwowanie RBAC testować
 * przez prawdziwy łańcuch security, nie przez atrapę.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(PortalSecurityProperties.class)
public class SecurityConfig {

    /**
     * Łańcuch dla /api/**. Uwierzytelnia LoopbackHeaderAuthenticationFilter; każdy endpoint
     * wymaga tożsamości, a RBAC per zasób egzekwuje @PreAuthorize + AccessFacade (bean "access").
     * CSRF: brak cookies i sesji, ale SSO Kerberos to "ambient credential" — ochronę przed
     * żądaniami cross-site daje SameOriginRequestFilter (rejestracja w WebFiltersConfiguration).
     */
    @Bean
    @Order(1)
    SecurityFilterChain apiFilterChain(HttpSecurity http,
                                       AdGroupResolver adGroupResolver,
                                       PortalSecurityProperties properties) throws Exception {
        var headerAuthenticationFilter = new LoopbackHeaderAuthenticationFilter(adGroupResolver, properties);
        http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.disable())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(new ApiAuthenticationEntryPoint()))
                .addFilterBefore(headerAuthenticationFilter, AnonymousAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Łańcuch dla /apps/** (ADR-0007): moduły aplikacyjne przechodzą przez aplikację,
     * nie przez statykę IIS. Tożsamość jak w /api (ten sam filtr nagłówka, osobna
     * instancja — filtr jest per łańcuch); RBAC per kafelek egzekwuje AppsController
     * jawnie przez AccessFacade, bo odmowa ma być stroną HTML, nie JSON-em.
     */
    @Bean
    @Order(2)
    SecurityFilterChain appsFilterChain(HttpSecurity http,
                                        AdGroupResolver adGroupResolver,
                                        PortalSecurityProperties properties) throws Exception {
        var headerAuthenticationFilter = new LoopbackHeaderAuthenticationFilter(adGroupResolver, properties);
        http
                .securityMatcher("/apps/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.disable())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(new AppsAuthenticationEntryPoint()))
                .addFilterBefore(headerAuthenticationFilter, AnonymousAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Wszystko poza /api/** i /apps/**: jawne deny-by-default. Jedyny wyjątek to health/info
     * (sonda WinSW i diagnostyka) — i tak dostępne tylko z maszyny (bind na loopbacku).
     * W dev PRZED tym łańcuchem stoi devStaticFilterChain (statyczny frontend) — patrz
     * DevSecurityConfiguration; w prod tego beana nie ma i deny-all obowiązuje bez wyjątków.
     */
    @Bean
    @Order(3)
    SecurityFilterChain fallbackFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().denyAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
