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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolver grup dla środowisk bez AD (dev, testy): mapa login -> grupy z konfiguracji.
 * Loginy porównywane case-insensitive, tak jak robi to AD.
 */
final class StaticAdGroupResolver implements AdGroupResolver {

    private final Map<String, Set<String>> groupsByLogin;

    StaticAdGroupResolver(Map<String, List<String>> staticGroups) {
        this.groupsByLogin = staticGroups.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        entry -> entry.getKey().toLowerCase(Locale.ROOT),
                        entry -> Set.copyOf(entry.getValue())));
    }

    @Override
    public Set<String> resolveGroups(String samAccountName) {
        return groupsByLogin.getOrDefault(samAccountName.toLowerCase(Locale.ROOT), Set.of());
    }
}
