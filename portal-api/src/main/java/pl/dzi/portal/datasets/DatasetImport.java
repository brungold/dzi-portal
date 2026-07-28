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

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("dataset_imports")
record DatasetImport(
        @Id Long id,
        Long datasetId,
        String filename,
        String importedBy,
        Instant tsUtc,
        String status,
        int rowsInserted,
        int rowsUpdated,
        int rowsUnchanged,
        int rowsMissing,
        String errorReport) {

    static DatasetImport ok(long datasetId, String filename, String user, Instant now,
                            int inserted, int updated, int unchanged, int missing) {
        return new DatasetImport(null, datasetId, filename, user, now, "OK",
                inserted, updated, unchanged, missing, null);
    }

    static DatasetImport rejected(long datasetId, String filename, String user, Instant now, String errorReport) {
        return new DatasetImport(null, datasetId, filename, user, now, "REJECTED", 0, 0, 0, 0, errorReport);
    }
}
