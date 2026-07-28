/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Pułapka, której ta klasa świadomie unika: "pakiet auto-konfiguracji" to zawsze pakiet
 * klasy @SpringBootApplication (tu: pl.dzi.portal.worker), NIEZALEŻNIE od scanBasePackages.
 * Bez jawnego @EnableJdbcRepositories Boot szukałby repozytoriów tylko w .worker
 * i nie znalazłby TaskRepository z portal-common.
 */
@SpringBootApplication(scanBasePackages = "pl.dzi.portal")
@EnableJdbcRepositories(basePackages = "pl.dzi.portal.common")
@EnableScheduling
public class PortalWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortalWorkerApplication.class, args);
    }
}
