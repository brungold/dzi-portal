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

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Brama ścieżek: wszystko, co próbuje wyjść poza katalog bazowy, ma zwracać empty.
 * Testy FIRST: czysta funkcja, zero IO — pliki nie muszą istnieć, żeby ścieżkę odrzucić.
 */
class PathSafetyTest {

    private final Path base = Path.of("apps-base");

    @Test
    void shouldResolveNestedFileInsideBase() {
        var resolved = PathSafety.resolveInside(base, "red-pisma-sprawy/index.html");

        assertThat(resolved).isPresent();
        assertThat(resolved.get()).startsWith(base.toAbsolutePath().normalize());
        assertThat(resolved.get().getFileName().toString()).isEqualTo("index.html");
    }

    @Test
    void shouldRejectParentTraversal() {
        assertThat(PathSafety.resolveInside(base, "red-pisma-sprawy/../../etc/passwd")).isEmpty();
    }

    @Test
    void shouldRejectAnyDoubleDotSequenceEvenIfItWouldStayInside() {
        // Świadomie ostrzej niż normalize: ".." w URL-u modułu nie ma prawa bytu.
        assertThat(PathSafety.resolveInside(base, "red-pisma-sprawy/../red-pisma-sprawy/index.html")).isEmpty();
    }

    @Test
    void shouldRejectBackslashes() {
        assertThat(PathSafety.resolveInside(base, "red-pisma-sprawy\\index.html")).isEmpty();
    }

    @Test
    void shouldRejectNullByteAndBlank() {
        assertThat(PathSafety.resolveInside(base, "red\0.csv")).isEmpty();
        assertThat(PathSafety.resolveInside(base, "  ")).isEmpty();
        assertThat(PathSafety.resolveInside(base, null)).isEmpty();
    }
}
