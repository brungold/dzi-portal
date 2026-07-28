/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.common.audit;

/**
 * Pojedynczy wpis rejestru audytu. Znacznik czasu dokłada AuditWriter (Clock),
 * żeby żaden producent wpisów nie miał własnego pomysłu na czas.
 *
 * Trzy poziomy informacji:
 *  - warstwa HTTP (method, path, status) — wypełniana ZAWSZE przez AuditFilter,
 *  - action — nazwa akcji biznesowej (@Audited na endpointzie), np. TILE_EXECUTE,
 *  - objectRef — identyfikator obiektu, którego akcja dotyczy (AuditContext), np. "tile:42".
 */
public record AuditEntry(
        String username,
        String clientIp,
        String httpMethod,
        String path,
        String action,
        String objectRef,
        AuditStatus status,
        int httpStatus,
        long durationMs,
        String correlationId) {
}
