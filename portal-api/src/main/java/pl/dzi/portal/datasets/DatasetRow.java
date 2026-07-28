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
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AGREGAT Data JDBC: wiersz + jego komórki zapisują się i wersjonują RAZEM.
 * To tutaj mieszka wymagany optimistic locking: @Version na wierszu oznacza,
 * że save() przegranego w wyścigu edycji rzuca OptimisticLockingFailureException
 * (GlobalExceptionHandler mapuje na 409), a wygrany podbija wersję o 1.
 * Rekord niemutowalny — zmiany przez metody with*, jak w Task.
 */
@Table("dataset_rows")
record DatasetRow(
        @Id Long id,
        Long datasetId,
        String businessKey,
        @MappedCollection(idColumn = "row_id") Set<DatasetCell> cells,
        String updatedBy,
        Instant updatedAt,
        @Version int version) {

    static DatasetRow create(long datasetId, String businessKey, Map<String, String> cellValues,
                             String user, Instant now) {
        return new DatasetRow(null, datasetId, businessKey, toCells(cellValues), user, now, 0);
    }

    DatasetRow withCells(Map<String, String> cellValues, String user, Instant now) {
        return new DatasetRow(id, datasetId, businessKey, toCells(cellValues), user, now, version);
    }

    DatasetRow withCellValue(String columnCode, String value, String user, Instant now) {
        Map<String, String> updated = cellValues();
        updated.put(columnCode, value);
        return withCells(updated, user, now);
    }

    /** Mapa kod->wartość (HashMap, bo wartości bywają null — Map.of by wybuchło). */
    Map<String, String> cellValues() {
        Map<String, String> values = new java.util.HashMap<>();
        for (DatasetCell cell : cells) {
            values.put(cell.columnCode(), cell.cellValue());
        }
        return values;
    }

    Optional<String> cellValue(String columnCode) {
        return cells.stream()
                .filter(cell -> cell.columnCode().equals(columnCode))
                .map(DatasetCell::cellValue)
                .findFirst();
    }

    private static Set<DatasetCell> toCells(Map<String, String> cellValues) {
        return cellValues.entrySet().stream()
                .map(entry -> new DatasetCell(entry.getKey(), entry.getValue()))
                .collect(Collectors.toUnmodifiableSet());
    }
}
