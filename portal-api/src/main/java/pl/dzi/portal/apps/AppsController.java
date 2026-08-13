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
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import pl.dzi.portal.infrastructure.web.PortalRequestAttributes;
import pl.dzi.portal.tiles.AccessFacade;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Strażnik modułów aplikacyjnych (ADR-0007). IIS przekazuje tu całe /apps/** —
 * plik dostaje wyłącznie ten, kto ma wpis w tile_permissions dla kafelka o kodzie
 * równym pierwszemu segmentowi ścieżki. Ta sama tabela, która decyduje "czy widzisz
 * kafelek", decyduje więc "czy dostaniesz plik".
 *
 * Świadome decyzje:
 *  - jawne wywołanie AccessFacade zamiast @PreAuthorize: odmowa musi być stroną HTML
 *    dla człowieka (GlobalExceptionHandler zwróciłby JSON), a strażnik ma być widoczny
 *    w kodzie czarno na białym;
 *  - 404 dla nieistniejących i dla prób traversalu — bez zdradzania struktury dysku;
 *  - wydanie strumieniowe (FileSystemResource) — CSV ma 165 MB, readAllBytes odpada;
 *  - ETag (weak, mtime+rozmiar): przy fetch(..., cache:'no-cache') powtórne otwarcie
 *    modułu kończy się 304 bez transferu; 304 nie trafia do audytu (ADR-0002 pkt 2).
 */
@RestController
@RequiredArgsConstructor
class AppsController {

    static final String ACTION_OPEN = "APP_OPEN";
    static final String ACTION_DATA = "APP_DATA";
    static final String ACTION_DENIED = "APP_DENIED";

    private final AppsProperties properties;
    private final AccessFacade access;

    @GetMapping({"/apps", "/apps/"})
    ResponseEntity<String> appsRoot() {
        // Listy modułów nie ma i nie będzie — katalogiem jest strona główna portalu.
        return notFound();
    }

    @GetMapping("/apps/**")
    ResponseEntity<?> serve(HttpServletRequest request, Authentication authentication) {
        String uri = request.getRequestURI();
        String path = uri.substring("/apps/".length());

        int slash = path.indexOf('/');
        String code = slash < 0 ? path : path.substring(0, slash);
        String rest = slash < 0 ? "" : path.substring(slash + 1);

        if (code.isBlank()) {
            return notFound();
        }
        request.setAttribute(PortalRequestAttributes.OBJECT_REF, "tile:" + code);

        // "/apps/red-pisma-sprawy" bez ukośnika: przekierowanie zamiast treści, bo
        // względne ścieżki w index.html (app.js, module.css) liczą się od katalogu.
        if (slash < 0) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, uri + "/")
                    .build();
        }

        if (!access.canRead(code, authentication)) {
            request.setAttribute(PortalRequestAttributes.ACTION, ACTION_DENIED);
            return html(HttpStatus.FORBIDDEN, AppsErrorPages.forbidden());
        }

        String relativeFile = rest.isEmpty() || rest.endsWith("/") ? rest + "index.html" : rest;
        Path file = PathSafety.resolveInside(properties.baseDir(), code + "/" + relativeFile)
                .orElse(null);
        if (file == null || !Files.isRegularFile(file)) {
            return notFound();
        }

        String fileName = file.getFileName().toString();
        request.setAttribute(PortalRequestAttributes.ACTION, actionFor(fileName));

        var resource = new FileSystemResource(file);
        String etag = "W/\"" + resource.getFile().length() + "-" + resource.getFile().lastModified() + "\"";
        if (new ServletWebRequest(request).checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .header(HttpHeaders.CONTENT_TYPE, AppsMediaTypes.forFileName(fileName))
                .body(resource);
    }

    /** Wejście do modułu i pliki danych mają w audycie własne akcje (polityka: AppsAuditPolicy). */
    private static String actionFor(String fileName) {
        String extension = AppsMediaTypes.extensionOf(fileName);
        if ("html".equals(extension) || "htm".equals(extension)) {
            return ACTION_OPEN;
        }
        if (AppsAuditPolicy.DATA_EXTENSIONS.contains(extension)) {
            return ACTION_DATA;
        }
        return null;
    }

    private static ResponseEntity<String> notFound() {
        return html(HttpStatus.NOT_FOUND, AppsErrorPages.notFound());
    }

    private static ResponseEntity<String> html(HttpStatus status, String body) {
        return ResponseEntity.status(status)
                .contentType(new MediaType(MediaType.TEXT_HTML, java.nio.charset.StandardCharsets.UTF_8))
                .body(body);
    }
}
