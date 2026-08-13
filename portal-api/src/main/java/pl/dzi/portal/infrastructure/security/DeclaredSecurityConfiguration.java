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

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import pl.dzi.portal.apps.AppsAuthenticationEntryPoint;

import java.time.Clock;

/**
 * Profil {@code declared} — deklarowana tożsamość: ADR-0003 (granice zaufania,
 * limiter, audyt) + ADR-0005 (deklarowany departament — bez rejestru użytkowników).
 *
 * Wspierane kombinacje: {@code dev,declared} (laptop, SQL Express) oraz {@code declared}
 * z własną konfiguracją datasource (serwer). Kombinacja {@code prod,declared} celowo
 * NIE działa: prod aktywuje LdapConfiguration (wymagane env LDAP), a założeniem tego
 * trybu jest właśnie brak integracji z katalogiem.
 */
@Slf4j
@Configuration
@Profile("declared")
@EnableConfigurationProperties(DeclaredIdentityProperties.class)
class DeclaredSecurityConfiguration {

    @Bean
    DeclaredRateLimiter declaredRateLimiter(DeclaredIdentityProperties properties, Clock clock) {
        return new DeclaredRateLimiter(properties, clock);
    }

    /**
     * Łańcuch dla /api/** w trybie declared. Order -10: musi stanąć PRZED apiFilterChain
     * z SecurityConfig (@Order(1)) — oba łapią /api/**, wygrywa niższy numer, więc pod tym
     * profilem wariant A jest w całości przesłonięty. devStaticFilterChain (@Order(0))
     * ma rozłączny matcher (statyka), kolizji nie ma. Poza profilem declared beana nie ma
     * i łańcuchy wariantu A działają bajt w bajt jak dotąd.
     */
    @Bean
    @Order(-10)
    SecurityFilterChain declaredApiFilterChain(HttpSecurity http,
                                               PortalSecurityProperties securityProperties,
                                               DeclaredIdentityProperties declaredProperties,
                                               DeclaredRateLimiter rateLimiter) throws Exception {
        var declaredFilter = new DeclaredHeaderAuthenticationFilter(
                securityProperties, declaredProperties, rateLimiter);
        http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.disable())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(new ApiAuthenticationEntryPoint()))
                .addFilterBefore(declaredFilter, AnonymousAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Łańcuch dla /apps/** w trybie declared (ADR-0007). Order -9: przed appsFilterChain
     * z SecurityConfig (@Order(2)) — analogicznie do pary -10/1 dla /api. Osobna instancja
     * filtra (filtr jest per łańcuch), współdzielone properties i limiter.
     */
    @Bean
    @Order(-9)
    SecurityFilterChain declaredAppsFilterChain(HttpSecurity http,
                                                PortalSecurityProperties securityProperties,
                                                DeclaredIdentityProperties declaredProperties,
                                                DeclaredRateLimiter rateLimiter) throws Exception {
        var declaredFilter = new DeclaredHeaderAuthenticationFilter(
                securityProperties, declaredProperties, rateLimiter);
        http
                .securityMatcher("/apps/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.disable())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(new AppsAuthenticationEntryPoint()))
                .addFilterBefore(declaredFilter, AnonymousAuthenticationFilter.class);
        return http.build();
    }

    /** Baner jak w demo: tryb ma się głośno przedstawiać przy każdym starcie. */
    @Bean
    ApplicationRunner declaredBanner(DeclaredIdentityProperties properties) {
        return args -> log.warn("""

                ==========================================================================
                 PROFIL DECLARED: aplikacja UFA nagłówkowi X-Auth-User z zaufanych
                 źródeł (loopback + CIDR-y: {}).
                  - PROD: nagłówek wypełnia moduł IIS PO Windows Authentication —
                    login jest UWIERZYTELNIONY i niepodrabialny, a X-Auth-Dept od
                    klienta jest usuwany (ADR-0006); departament wróci z modułem v2
                  - DEV (bez IIS): nagłówek pochodzi z deklaracji frontendu lub
                    dev-fallbacku — tożsamość NIE jest wtedy uwierzytelniana
                  - uprawnienia żądania = login+wszyscy vs tile_permissions
                  - kompensacje: audyt append-only (kto, co, skąd), limit żądań
                    ({}/min) i blokada adresu deklarującego >{} loginów w oknie {}
                ==========================================================================""",
                properties.allowedCidrs().isEmpty() ? "(brak — tylko loopback)" : properties.allowedCidrs(),
                properties.maxRequestsPerMinute(),
                properties.anomalyDistinctLogins(),
                properties.anomalyWindow());
    }
}
