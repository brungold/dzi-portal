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
import org.springframework.context.annotation.Profile;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

import java.time.Clock;
import java.util.Map;

/**
 * Produkcyjne źródło grup: LDAPS do kontrolera domeny + cache TTL.
 *
 * Zmiana w Etapie 1 (pierwszy kontakt z prawdziwym DC): twarde timeouty połączenia
 * i odczytu. Bez nich niedostępny kontroler domeny (firewall, awaria) blokuje wątek
 * żądania na domyślne kilkadziesiąt sekund — a to dzieje się przy PIERWSZYM żądaniu
 * użytkownika spoza cache'u, czyli wygląda jak "portal się zawiesił".
 */
@Configuration
@Profile("prod")
@EnableConfigurationProperties(PortalLdapProperties.class)
class LdapConfiguration {

    private static final String CONNECT_TIMEOUT_MS = "5000";
    private static final String READ_TIMEOUT_MS = "10000";

    @Bean
    LdapContextSource ldapContextSource(PortalLdapProperties properties) {
        var contextSource = new LdapContextSource();
        contextSource.setUrl(properties.url());
        contextSource.setBase(properties.base());
        contextSource.setUserDn(properties.userDn());
        contextSource.setPassword(properties.password());
        contextSource.setBaseEnvironmentProperties(Map.of(
                "com.sun.jndi.ldap.connect.timeout", CONNECT_TIMEOUT_MS,
                "com.sun.jndi.ldap.read.timeout", READ_TIMEOUT_MS));
        return contextSource;
    }

    @Bean
    LdapTemplate ldapTemplate(LdapContextSource contextSource) {
        var template = new LdapTemplate(contextSource);
        // AD zwraca referrale przy przeszukiwaniu od korzenia domeny; bez tej flagi
        // spring-ldap rzuca PartialResultException mimo poprawnych wyników.
        template.setIgnorePartialResultException(true);
        return template;
    }

    @Bean
    AdGroupResolver adGroupResolver(LdapTemplate ldapTemplate,
                                    PortalLdapProperties ldapProperties,
                                    PortalSecurityProperties securityProperties,
                                    Clock clock) {
        return new CachingAdGroupResolver(
                new LdapAdGroupResolver(ldapTemplate, ldapProperties),
                securityProperties.groupCacheTtl(),
                clock);
    }
}
