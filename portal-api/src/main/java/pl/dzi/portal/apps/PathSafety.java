/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.apps;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Jedyna brama między ścieżką z URL-a a systemem plików. Strażnik wydaje pliki
 * z katalogu bazowego i TYLKO z niego — każda próba wyjścia (path traversal)
 * kończy się pustym Optionalem, który kontroler zamienia na 404.
 *
 * Zasada: nie "wykrywamy ataki", tylko normalizujemy i sprawdzamy, czy wynik
 * wciąż leży pod bazą. To odporne także na sekwencje, których nie przewidzieliśmy.
 */
final class PathSafety {

    private PathSafety() {
    }

    /**
     * @param base     katalog bazowy (zostanie znormalizowany do ścieżki absolutnej)
     * @param relative ścieżka względna z URL-a (już zdekodowana przez Springa)
     * @return ścieżka wewnątrz bazy albo empty, gdy żądanie próbuje wyjść poza nią
     */
    static Optional<Path> resolveInside(Path base, String relative) {
        if (relative == null || relative.isBlank()) {
            return Optional.empty();
        }
        // Znaki, które w poprawnym URL-u modułu nie mają prawa wystąpić.
        if (relative.indexOf('\0') >= 0 || relative.indexOf('\\') >= 0 || relative.contains("..")) {
            return Optional.empty();
        }
        Path normalizedBase = base.toAbsolutePath().normalize();
        Path candidate = normalizedBase.resolve(relative).normalize();
        if (!candidate.startsWith(normalizedBase)) {
            return Optional.empty();
        }
        return Optional.of(candidate);
    }
}
