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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.dzi.portal.tiles.AccessFacade;

import java.time.Clock;

@Configuration
class DatasetsConfiguration {

    @Bean
    DatasetsFacade datasetsFacade(DatasetRepository datasets, DatasetColumnRepository columns,
                                  DatasetRowRepository rows, DatasetImportRepository imports,
                                  AccessFacade access, Clock clock) {
        return new DatasetsFacade(datasets, columns, rows, imports, access, new XlsxParser(), clock);
    }
}
