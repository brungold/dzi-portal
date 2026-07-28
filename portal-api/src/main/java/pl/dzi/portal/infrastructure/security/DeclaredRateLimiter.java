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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ogranicznik żądań dla profilu {@code declared} — dwie funkcje w jednym stanie per adres:
 *
 *  1) SUFIT CZĘSTOTLIWOŚCI: proste okno przesuwne (ostatnia minuta). Tłumi zapętlone
 *     skrypty i przypadkowe pętle, nie przeszkadza przeglądarce.
 *  2) DETEKCJA ANOMALII: liczba RÓŻNYCH deklarowanych loginów z jednego adresu w oknie.
 *     Podszywaniu się w modelu deklaracji NIE DA SIĘ zapobiec (brak sekretu — ADR-0003),
 *     ale enumeracja/testowanie cudzych loginów ma wyraźną sygnaturę: jeden adres, wiele
 *     tożsamości. Przekroczenie progu = blokada adresu i wpis DENIED w audycie (przez
 *     AuditFilter, który widzi 429 razem z deklarowanym loginem).
 *
 * Świadomie w pamięci, bez zależności (por. decyzja 5 z ADR-0001 o własnym cache TTL):
 * restart zeruje liczniki, co przy tej funkcji (tłumik + alarm, nie księgowość) jest
 * akceptowalne. Higiena pamięci: stan adresu przycinany przy każdym użyciu, a przy
 * przekroczeniu MAX_TRACKED_ADDRESSES usuwane są wpisy bezczynne dłużej niż okno.
 */
@Slf4j
final class DeclaredRateLimiter {

    enum Decision { ALLOW, THROTTLED, BLOCKED }

    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);
    private static final int MAX_TRACKED_ADDRESSES = 10_000;

    private final DeclaredIdentityProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, AddressState> states = new ConcurrentHashMap<>();

    DeclaredRateLimiter(DeclaredIdentityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Rejestruje żądanie i orzeka: przepuścić, przydusić (limit) czy odciąć (anomalia). */
    Decision register(String clientIp, String declaredLogin) {
        Instant now = clock.instant();
        evictIdleIfOversized(now);
        AddressState state = states.computeIfAbsent(clientIp, ip -> new AddressState());
        synchronized (state) {
            state.prune(now, properties.anomalyWindow());

            if (state.blockedUntil != null && state.blockedUntil.isAfter(now)) {
                return Decision.BLOCKED;
            }
            if (state.hits.size() >= properties.maxRequestsPerMinute()) {
                return Decision.THROTTLED;
            }

            state.hits.addLast(now);
            state.lastSeenByLogin.put(declaredLogin, now);

            if (state.lastSeenByLogin.size() > properties.anomalyDistinctLogins()) {
                state.blockedUntil = now.plus(properties.blockDuration());
                log.warn("Anomalia deklaracji tożsamości: {} różnych loginów z adresu {} "
                                + "w oknie {} — blokada do {}. Ostatnio zadeklarowany: {}",
                        state.lastSeenByLogin.size(), clientIp, properties.anomalyWindow(),
                        state.blockedUntil, declaredLogin);
                return Decision.BLOCKED;
            }
            return Decision.ALLOW;
        }
    }

    private void evictIdleIfOversized(Instant now) {
        if (states.size() <= MAX_TRACKED_ADDRESSES) {
            return;
        }
        Instant idleThreshold = now.minus(properties.anomalyWindow())
                .minus(properties.blockDuration());
        states.entrySet().removeIf(entry -> entry.getValue().idleSince(idleThreshold));
    }

    /** Stan per adres — dostęp wyłącznie pod monitorem instancji (synchronized wyżej). */
    private static final class AddressState {
        private final Deque<Instant> hits = new ArrayDeque<>();
        private final Map<String, Instant> lastSeenByLogin = new HashMap<>();
        private Instant blockedUntil;
        private Instant lastActivity = Instant.EPOCH;

        private void prune(Instant now, Duration anomalyWindow) {
            lastActivity = now;
            Instant rateCutoff = now.minus(RATE_WINDOW);
            while (!hits.isEmpty() && hits.peekFirst().isBefore(rateCutoff)) {
                hits.removeFirst();
            }
            Instant loginCutoff = now.minus(anomalyWindow);
            lastSeenByLogin.values().removeIf(seen -> seen.isBefore(loginCutoff));
            if (blockedUntil != null && !blockedUntil.isAfter(now)) {
                blockedUntil = null;
            }
        }

        private synchronized boolean idleSince(Instant threshold) {
            return lastActivity.isBefore(threshold)
                    && (blockedUntil == null || blockedUntil.isBefore(threshold));
        }
    }
}
