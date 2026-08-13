/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.infrastructure.audit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Decyzja "czy ten wpis trafia do rejestru" — podejmowana per rejestracja filtra,
 * nie per żądanie w kodzie filtra. /api/* audytuje wszystko (ALWAYS, stan sprzed
 * zmiany — bajt w bajt), /apps/* selektywnie (AppsAuditPolicy, ADR-0007/D3).
 *
 * Wyjątki od polityki zaszyte w AuditFilter i nienegocjowalne:
 * 304 nigdy (ADR-0002 pkt 2), żądanie przerwane wyjątkiem zawsze (ERROR/500).
 */
@FunctionalInterface
public interface AuditPolicy {

    AuditPolicy ALWAYS = (request, httpStatus) -> true;

    boolean shouldWrite(HttpServletRequest request, int httpStatus);
}
