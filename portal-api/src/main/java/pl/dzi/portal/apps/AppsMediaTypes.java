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

import java.util.Locale;
import java.util.Map;

/**
 * Content-Type po rozszerzeniu — świadomie własna, krótka mapa zamiast wykrywania
 * po zawartości: zbiór typów w modułach jest zamknięty i znany, a "text/*" musi
 * nieść charset=utf-8 (lekcja z CSV: bez charsetu przeglądarka zgaduje i psuje ogonki).
 */
final class AppsMediaTypes {

    private static final Map<String, String> BY_EXTENSION = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("htm", "text/html; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"),
            Map.entry("mjs", "text/javascript; charset=utf-8"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("csv", "text/csv; charset=utf-8"),
            Map.entry("txt", "text/plain; charset=utf-8"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("map", "application/json"));

    static final String DEFAULT = "application/octet-stream";

    private AppsMediaTypes() {
    }

    static String forFileName(String fileName) {
        return BY_EXTENSION.getOrDefault(extensionOf(fileName), DEFAULT);
    }

    static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
