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

import jakarta.servlet.http.HttpServletRequest;
import pl.dzi.portal.infrastructure.audit.AuditPolicy;

import java.util.Locale;
import java.util.Set;

/**
 * Selektywny audyt /apps/** (ADR-0007, rozstrzygnięcie D3): otwarcie modułu to 4-5 żądań
 * (html, css, js, logo, csv) — zapis wszystkich zalałby rejestr i ukrył zdarzenia istotne
 * (ta sama logika co wyłączenie 304 w ADR-0002 pkt 2).
 *
 * Zapisujemy: każdą odmowę i błąd (>=400), wejście do modułu (dokument HTML, także
 * katalog z domyślnym index.html) oraz pobrania plików danych. Pomijamy zasoby
 * towarzyszące (css/js/obrazki/fonty) — o ile wydały się poprawnie.
 *
 * Plik danych (aneks ADR-0007, 2026-09): rozszerzenie z DATA_EXTENSIONS ALBO położenie
 * w podkatalogu {@code data/} modułu — niezależnie od rozszerzenia. Powód: moduły
 * trzymają dane także w plikach .js (agregaty ReD, {@code data/*.js}), a rozszerzenie
 * .js samo w sobie oznacza zasób towarzyszący. Konwencja dla autorów modułów:
 * dane do {@code data/} (apps/README.md).
 */
public final class AppsAuditPolicy implements AuditPolicy {

    /** Rozszerzenia plików danych — audytowane zawsze (spójnie z AppsController.ACTION_DATA). */
    static final Set<String> DATA_EXTENSIONS = Set.of("csv", "xlsx", "xls", "json", "pdf", "xml", "txt");

    /** Podkatalog modułu, którego cała zawartość jest danymi (bez względu na rozszerzenie). */
    static final String DATA_DIRECTORY = "data/";

    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("html", "htm");

    @Override
    public boolean shouldWrite(HttpServletRequest request, int httpStatus) {
        if (httpStatus >= 400) {
            return true; // odmowy i błędy zawsze — to jest właśnie materiał dla audytora
        }
        String path = request.getRequestURI();
        if (path.endsWith("/")) {
            return true; // katalog modułu => serwowany index.html => wejście do modułu
        }
        String relative = relativeWithinModule(path);
        String extension = AppsMediaTypes.extensionOf(relative);
        return DOCUMENT_EXTENSIONS.contains(extension) || isDataFile(relative);
    }

    /**
     * Czy ścieżka WZGLĘDEM katalogu modułu ({@code app.js}, {@code data/ko-01.js},
     * {@code dane_Aurea/plik.csv}) wskazuje plik danych.
     */
    static boolean isDataFile(String relativePath) {
        String normalized = relativePath.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.startsWith(DATA_DIRECTORY) || normalized.contains("/" + DATA_DIRECTORY)) {
            return true;
        }
        return DATA_EXTENSIONS.contains(AppsMediaTypes.extensionOf(normalized));
    }

    /** {@code /apps/<code>/dir/file} -> {@code dir/file}; poza /apps/ zwraca ścieżkę bez zmian. */
    static String relativeWithinModule(String requestUri) {
        String prefix = "/apps/";
        if (!requestUri.startsWith(prefix)) {
            return requestUri;
        }
        String afterPrefix = requestUri.substring(prefix.length());
        int slash = afterPrefix.indexOf('/');
        return slash < 0 ? "" : afterPrefix.substring(slash + 1);
    }
}
