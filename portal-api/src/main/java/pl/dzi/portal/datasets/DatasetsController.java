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

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import pl.dzi.portal.infrastructure.audit.AuditContext;
import pl.dzi.portal.infrastructure.audit.Audited;

import java.io.IOException;

/**
 *   Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 *   Autor: Maciej Myśliwiec, 2026.
 * REST zbiorów danych. Import zwraca raport w OBU przypadkach: 200 gdy merge przeszedł,
 * 422 gdy walidacja odrzuciła plik (raport JEST treścią odpowiedzi — to on jest
 * produktem walidacji, ProblemDetail nic by tu nie dodał). Celowo bez wyjątku:
 * typy raportu zostają wewnątrz modułu, GlobalExceptionHandler o nas nie wie.
 */
@RestController
@RequiredArgsConstructor
class DatasetsController {

    private final DatasetsFacade datasetsFacade;
    private final AuditContext auditContext;

    /**
     * ETag = token stanu zbioru (Etap 6). Klient odpytuje co 10 s z If-None-Match:
     * brak zmian => 304 za cenę jednej agregacji (i bez wpisu w audycie — patrz AuditFilter),
     * zmiana => pełny widok z nowym ETagiem. STOMP świadomie odłożony — próg w ADR-0002.
     */
    @Audited(action = "DATASET_VIEW")
    @GetMapping("/api/datasets/{code}")
    ResponseEntity<DatasetsFacade.DatasetView> view(@PathVariable String code,
                                                    @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false)
                                                    String ifNoneMatch,
                                                    Authentication authentication) {
        auditContext.setObjectRef("dataset:" + code);
        String etag = "\"" + datasetsFacade.stateToken(code, authentication) + "\"";
        if (etag.equals(normalizeEtag(ifNoneMatch))) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).body(datasetsFacade.view(code, authentication));
    }

    /** Tolerujemy słabe ETagi (W/) — my wydajemy mocne, ale proxy potrafią dopisać prefiks. */
    private static String normalizeEtag(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        String value = headerValue.trim();
        return value.startsWith("W/") ? value.substring(2) : value;
    }

    @Audited(action = "DATASET_IMPORT")
    @PostMapping("/api/datasets/{code}/import")
    ResponseEntity<DatasetsFacade.ImportReport> importFile(@PathVariable String code,
                                                           @RequestParam("file") MultipartFile file,
                                                           Authentication authentication) throws IOException {
        auditContext.setObjectRef("dataset:" + code);
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pusty plik");
        }
        DatasetsFacade.ImportReport report = datasetsFacade.importFile(code,
                file.getOriginalFilename(), file.getInputStream(), authentication);
        return "REJECTED".equals(report.status())
                ? ResponseEntity.unprocessableEntity().body(report)   // 422: audyt zapisze ERROR
                : ResponseEntity.ok(report);
    }

    record EditCellRequest(String columnCode, String value, int version) {
    }

    @Audited(action = "DATASET_EDIT")
    @PatchMapping("/api/datasets/{code}/rows/{rowId}")
    DatasetsFacade.EditedCell editCell(@PathVariable String code, @PathVariable long rowId,
                                       @RequestBody EditCellRequest request,
                                       Authentication authentication) {
        auditContext.setObjectRef("dataset:" + code + ":row:" + rowId);
        return datasetsFacade.editCell(code, rowId, request.columnCode(), request.value(),
                request.version(), authentication);
    }
}
