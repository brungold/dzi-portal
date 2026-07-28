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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * @param header          nazwa nagłówka z tożsamością, wstrzykiwanego przez IIS (LOGON_USER)
 * @param devFallbackUser DEV ONLY: użytkownik podstawiany przy braku nagłówka (żądanie i tak musi
 *                        przyjść z loopbacku); w prod jawnie pusty string
 * @param groupCacheTtl   TTL cache'u grup AD per użytkownik
 * @param staticGroups    DEV ONLY: mapa login -> grupy dla StaticAdGroupResolver
 */
@ConfigurationProperties(prefix = "portal.security")
record PortalSecurityProperties(
        @DefaultValue("X-Auth-User") String header,
        String devFallbackUser,
        @DefaultValue("10m") Duration groupCacheTtl,
        @DefaultValue Map<String, List<String>> staticGroups) {
}
