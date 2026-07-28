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
 * Zwraca grupy AD (sAMAccountName grup) dla użytkownika.
 * Kontrakt: pusty zbiór, gdy użytkownik nieznany lub bez grup portalowych — nigdy null, nigdy wyjątek "not found".
 *
 * Public świadomie (od Etapu 3): testy plasterkowe modułów biznesowych (np. tiles)
 * podstawiają własne implementacje, sterując personami przez nagłówek.
 */
public interface AdGroupResolver {

    Set<String> resolveGroups(String samAccountName);
}
