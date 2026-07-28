/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * Adres klienta do audytu + JEDYNA definicja "loopbacku" w systemie (poprawka DRY,
 * commit 31 — wcześniej zestaw adresów był zdublowany w filtrze uwierzytelniania).
 * X-Forwarded-For jest honorowany WYŁĄCZNIE, gdy bezpośrednim nadawcą jest loopback
 * (czyli IIS) — reguła w IIS ustawia ten nagłówek bezwarunkowo, więc wartość nie jest
 * doklejana do niczego, co przysłał klient.
 */
public final class ClientIpResolver {

    private static final Set<String> LOOPBACK_ADDRESSES = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private ClientIpResolver() {
    }

    public static boolean isLoopback(String remoteAddr) {
        return LOOPBACK_ADDRESSES.contains(remoteAddr);
    }

    public static String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!isLoopback(remoteAddr)) {
            return remoteAddr;
        }
        String forwarded = request.getHeader(X_FORWARDED_FOR);
        if (forwarded == null || forwarded.isBlank()) {
            return remoteAddr;
        }
        return forwarded.split(",")[0].trim();
    }
}
