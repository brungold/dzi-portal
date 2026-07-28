/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.datasets;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;

/**
 * Typy kolumn zbioru + kanonizacja wartości. Wszystkie wartości leżą w bazie jako tekst
 * w formacie KANONICZNYM (NUMBER: kropka dziesiętna bez zer wiodących, DATE: ISO yyyy-MM-dd,
 * BOOL: true/false) — dzięki temu merge porównuje stringi, a UI formatuje po swojemu.
 */
public enum ColumnType {

    TEXT {
        @Override
        public String canonicalize(String raw) {
            String trimmed = raw.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    },
    NUMBER {
        @Override
        public String canonicalize(String raw) {
            // Ludzie i Excel: "1 200,50" — przyjmujemy przecinek i spacje, kanonizujemy do kropki.
            String normalized = raw.trim().replace(" ", "").replace("\u00A0", "").replace(',', '.');
            if (normalized.isEmpty()) {
                return null;
            }
            try {
                return new BigDecimal(normalized).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("nie jest liczbą: '" + raw + "'");
            }
        }
    },
    DATE {
        private static final DateTimeFormatter POLISH = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        @Override
        public String canonicalize(String raw) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            try {
                return LocalDate.parse(trimmed).toString(); // ISO
            } catch (DateTimeParseException ignored) {
                // druga szansa: polski format
            }
            try {
                return LocalDate.parse(trimmed, POLISH).toString();
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("nie jest datą (yyyy-MM-dd lub dd.MM.yyyy): '" + raw + "'");
            }
        }
    },
    BOOL {
        private static final Set<String> TRUE_VALUES = Set.of("true", "tak", "1", "prawda");
        private static final Set<String> FALSE_VALUES = Set.of("false", "nie", "0", "fałsz", "falsz");

        @Override
        public String canonicalize(String raw) {
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                return null;
            }
            if (TRUE_VALUES.contains(normalized)) {
                return "true";
            }
            if (FALSE_VALUES.contains(normalized)) {
                return "false";
            }
            throw new IllegalArgumentException("nie jest wartością logiczną (tak/nie): '" + raw + "'");
        }
    };

    /** @return wartość kanoniczna albo null dla pustej; IllegalArgumentException gdy nieparsowalna. */
    public abstract String canonicalize(String raw);
}
