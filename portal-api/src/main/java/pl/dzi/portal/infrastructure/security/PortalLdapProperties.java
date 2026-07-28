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

/**
 * @param url         ldaps://... (AD wymusza podpisywanie; plain 389 + simple bind zostanie odrzucone)
 * @param base        baza wyszukiwania, np. DC=dzi,DC=pl
 * @param userDn      dedykowane konto read-only do LDAP — jedyny sekret systemu
 * @param password    hasło konta z env (WinSW), nigdy w plikach repo
 * @param groupPrefix zwracamy wyłącznie grupy portalowe — użytkownik w ARiMR należy do dziesiątek grup
 */
@ConfigurationProperties(prefix = "portal.ldap")
record PortalLdapProperties(
        String url,
        String base,
        String userDn,
        String password,
        @DefaultValue("DZI-Portal-") String groupPrefix) {
}
