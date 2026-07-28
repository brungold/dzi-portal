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

import java.util.Set;

/**
 * Principal portalu: login (sAMAccountName, bez domeny) + grupy AD.
 * Świadomie płaski rekord — wszystko, czego potrzebuje RBAC, i nic więcej.
 */
public record PortalUser(String login, Set<String> groups) {
}
