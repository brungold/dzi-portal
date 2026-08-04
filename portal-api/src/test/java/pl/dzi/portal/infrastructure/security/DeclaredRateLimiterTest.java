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

import org.junit.jupiter.api.Test;
import pl.dzi.portal.infrastructure.security.DeclaredRateLimiter.Decision;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeclaredRateLimiterTest {

    private static final String IP = "10.20.1.5";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-07-21T10:00:00Z"));

    @Test
    void should_allow_requests_under_the_per_minute_ceiling() {
        var limiter = limiter(5, 3);

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.register(IP, "jkowalski")).isEqualTo(Decision.ALLOW);
        }
        assertThat(limiter.register(IP, "jkowalski")).isEqualTo(Decision.THROTTLED);
    }

    @Test
    void should_release_throttle_after_rate_window_passes() {
        var limiter = limiter(1, 3);

        assertThat(limiter.register(IP, "jkowalski")).isEqualTo(Decision.ALLOW);
        assertThat(limiter.register(IP, "jkowalski")).isEqualTo(Decision.THROTTLED);

        clock.advance(Duration.ofSeconds(61));
        assertThat(limiter.register(IP, "jkowalski")).isEqualTo(Decision.ALLOW);
    }

    @Test
    void should_block_address_declaring_more_distinct_logins_than_threshold() {
        // sygnatura podszywania: jeden adres, wiele tożsamości — próg 3, czwarty login blokuje
        var limiter = limiter(100, 3);

        assertThat(limiter.register(IP, "anna")).isEqualTo(Decision.ALLOW);
        assertThat(limiter.register(IP, "beata")).isEqualTo(Decision.ALLOW);
        assertThat(limiter.register(IP, "celina")).isEqualTo(Decision.ALLOW);
        assertThat(limiter.register(IP, "dorota")).isEqualTo(Decision.BLOCKED);

        // blokada trzyma także znane wcześniej loginy z tego adresu
        assertThat(limiter.register(IP, "anna")).isEqualTo(Decision.BLOCKED);
        // ...ale nie dotyka innych adresów
        assertThat(limiter.register("10.20.9.9", "anna")).isEqualTo(Decision.ALLOW);
    }

    @Test
    void should_unblock_address_after_block_duration() {
        var limiter = limiter(100, 1);

        assertThat(limiter.register(IP, "anna")).isEqualTo(Decision.ALLOW);
        assertThat(limiter.register(IP, "beata")).isEqualTo(Decision.BLOCKED);

        // po blokadzie ORAZ wypadnięciu starych loginów z okna adres wraca do łask
        clock.advance(Duration.ofMinutes(16));
        assertThat(limiter.register(IP, "beata")).isEqualTo(Decision.ALLOW);
    }

    @Test
    void should_forget_logins_outside_anomaly_window() {
        var limiter = limiter(100, 2);

        assertThat(limiter.register(IP, "anna")).isEqualTo(Decision.ALLOW);
        assertThat(limiter.register(IP, "beata")).isEqualTo(Decision.ALLOW);

        // stare deklaracje wypadają z okna — trzecia tożsamość PO oknie nie jest anomalią
        clock.advance(Duration.ofMinutes(11));
        assertThat(limiter.register(IP, "celina")).isEqualTo(Decision.ALLOW);
    }

    private DeclaredRateLimiter limiter(int maxPerMinute, int anomalyDistinctLogins) {
        var properties = new DeclaredIdentityProperties(
                List.of(), maxPerMinute, anomalyDistinctLogins,
                Duration.ofMinutes(10), Duration.ofMinutes(15), "X-Auth-Dept");
        return new DeclaredRateLimiter(properties, clock);
    }

    /** Zegar przesuwany ręcznie — deterministyczne testy okien czasowych (FIRST). */
    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant start) {
            this.now = start;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
