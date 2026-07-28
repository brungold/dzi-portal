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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dekorator z cache'em TTL per użytkownik — zapytanie o zagnieżdżone grupy na dużym AD
 * (~11,5 tys. kont) bywa wolne, a grupy zmieniają się rzadko.
 *
 * Świadomie ręczna implementacja zamiast Caffeine: rozmiar jest naturalnie ograniczony
 * liczbą pracowników, a leniwa ewaluacja przy odczycie wystarcza. Próg wymiany na Caffeine
 * opisany w ADR-0001 (np. potrzeba metryk trafień albo aktywnej ewikcji).
 */
final class CachingAdGroupResolver implements AdGroupResolver {

    private final AdGroupResolver delegate;
    private final Duration ttl;
    private final Clock clock;
    private final ConcurrentHashMap<String, CachedEntry> cache = new ConcurrentHashMap<>();

    CachingAdGroupResolver(AdGroupResolver delegate, Duration ttl, Clock clock) {
        this.delegate = delegate;
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public Set<String> resolveGroups(String samAccountName) {
        String key = samAccountName.toLowerCase(Locale.ROOT);
        Instant now = clock.instant();

        CachedEntry hit = cache.get(key);
        if (hit != null && hit.expiresAt().isAfter(now)) {
            return hit.groups();
        }
        Set<String> groups = delegate.resolveGroups(samAccountName);
        cache.put(key, new CachedEntry(groups, now.plus(ttl)));
        return groups;
    }

    private record CachedEntry(Set<String> groups, Instant expiresAt) {
    }
}
