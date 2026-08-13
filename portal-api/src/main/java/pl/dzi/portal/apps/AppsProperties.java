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

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * Konfiguracja strażnika modułów (ADR-0007).
 *
 * @param baseDir katalog bazowy modułów NA ZEWNĄTRZ witryny IIS
 *                (prod: D:/portal/apps, dev: ../apps względem katalogu portal-api).
 *                Pierwszy segment ścieżki URL pod /apps/ = nazwa podkatalogu = code kafelka.
 */
@ConfigurationProperties(prefix = "portal.apps")
public record AppsProperties(Path baseDir) {
}
