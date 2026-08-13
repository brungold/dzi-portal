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

import java.util.Set;

/**
 * Selektywny audyt /apps/** (ADR-0007, rozstrzygnięcie D3): otwarcie modułu to 4-5 żądań
 * (html, css, js, logo, csv) — zapis wszystkich zalałby rejestr i ukrył zdarzenia istotne
 * (ta sama logika co wyłączenie 304 w ADR-0002 pkt 2).
 *
 * Zapisujemy: każdą odmowę i błąd (>=400), wejście do modułu (dokument HTML, także
 * katalog z domyślnym index.html) oraz pobrania plików danych. Pomijamy zasoby
 * towarzyszące (css/js/obrazki/fonty) — o ile wydały się poprawnie.
 */
public final class AppsAuditPolicy implements AuditPolicy {

    /** Rozszerzenia plików danych — audytowane zawsze (spójnie z AppsController.ACTION_DATA). */
    static final Set<String> DATA_EXTENSIONS = Set.of("csv", "xlsx", "json");

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
        String extension = AppsMediaTypes.extensionOf(path);
        return DOCUMENT_EXTENSIONS.contains(extension) || DATA_EXTENSIONS.contains(extension);
    }
}
