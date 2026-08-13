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

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Moduł strażnika: kontroler i polityka audytu są komponentami/beanami rejestrowanymi
 * gdzie indziej (WebFiltersConfiguration tworzy politykę wprost — patrz komentarz tam);
 * tu wyłącznie włączenie properties. Fasada dostępu przychodzi z TilesConfiguration
 * (bean "access") — strażnik świadomie NIE ma własnej logiki uprawnień.
 */
@Configuration
@EnableConfigurationProperties(AppsProperties.class)
class AppsConfiguration {
}
