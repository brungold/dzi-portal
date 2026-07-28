/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Klasa główna celowo w pakiecie pl.dzi.portal (nie .api): dzięki temu
 * pl.dzi.portal.common z modułu portal-common jest podpakietem i Boot
 * sam znajduje repozytoria Spring Data JDBC oraz beany common — bez adnotacji.
 */
@SpringBootApplication
public class PortalApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortalApiApplication.class, args);
    }
}
