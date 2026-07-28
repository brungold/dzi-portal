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

import org.springframework.dao.OptimisticLockingFailureException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Double'y modułu datasets. InMemoryDatasetRowRepository odwzorowuje semantykę
 * @Version z Data JDBC: zapis ze starą wersją rzuca OptimisticLockingFailureException,
 * udany zapis podbija wersję o 1 (insert zostaje na 0) — dzięki temu testy fasady
 * ćwiczą DOKŁADNIE ten sam kontrakt, który na produkcji egzekwuje baza.
 */
final class InMemoryDatasetRepositories {

    private InMemoryDatasetRepositories() {
    }

    static final class InMemoryDatasetRepository implements DatasetRepository {
        private final Dataset dataset;
        private final String tileCode;

        InMemoryDatasetRepository(Dataset dataset, String tileCode) {
            this.dataset = dataset;
            this.tileCode = tileCode;
        }

        @Override
        public Optional<Dataset> findByCode(String code) {
            return dataset.code().equals(code) ? Optional.of(dataset) : Optional.empty();
        }

        @Override
        public Optional<String> findTileCodeFor(String datasetCode) {
            return dataset.code().equals(datasetCode) ? Optional.ofNullable(tileCode) : Optional.empty();
        }
    }

    static final class InMemoryDatasetColumnRepository implements DatasetColumnRepository {
        private final List<DatasetColumn> columns;

        InMemoryDatasetColumnRepository(List<DatasetColumn> columns) {
            this.columns = columns;
        }

        @Override
        public List<DatasetColumn> findByDatasetIdOrdered(long datasetId) {
            return columns.stream().filter(column -> column.datasetId().equals(datasetId)).toList();
        }
    }

    static final class InMemoryDatasetRowRepository implements DatasetRowRepository {
        private final Map<Long, DatasetRow> rowsById = new HashMap<>();
        private long nextId = 1;

        @Override
        public DatasetRow save(DatasetRow row) {
            if (row.id() == null) {
                DatasetRow inserted = new DatasetRow(nextId++, row.datasetId(), row.businessKey(),
                        row.cells(), row.updatedBy(), row.updatedAt(), 0);
                rowsById.put(inserted.id(), inserted);
                return inserted;
            }
            DatasetRow stored = rowsById.get(row.id());
            if (stored == null || stored.version() != row.version()) {
                throw new OptimisticLockingFailureException("wersja " + row.version() + " jest nieaktualna");
            }
            DatasetRow updated = new DatasetRow(row.id(), row.datasetId(), row.businessKey(),
                    row.cells(), row.updatedBy(), row.updatedAt(), row.version() + 1);
            rowsById.put(updated.id(), updated);
            return updated;
        }

        @Override
        public Optional<DatasetRow> findById(Long id) {
            return Optional.ofNullable(rowsById.get(id));
        }

        @Override
        public List<DatasetRow> findByDatasetIdOrderByBusinessKey(long datasetId) {
            return rowsById.values().stream()
                    .filter(row -> row.datasetId().equals(datasetId))
                    .sorted(java.util.Comparator.comparing(DatasetRow::businessKey))
                    .toList();
        }

        /** Izolacja testów (FIRST): świeży stan przed każdą metodą testu plasterkowego. */
        public void reset() {
            rowsById.clear();
            nextId = 1;
        }

        @Override
        public long countByDatasetId(long datasetId) {
            return rowsById.values().stream()
                    .filter(row -> row.datasetId().equals(datasetId)).count();
        }

        @Override
        public long countMissingKeys(long datasetId, java.util.Collection<String> keys) {
            return rowsById.values().stream()
                    .filter(row -> row.datasetId().equals(datasetId))
                    .filter(row -> !keys.contains(row.businessKey().toLowerCase(java.util.Locale.ROOT)))
                    .count();
        }

        @Override
        public String stateToken(long datasetId) {
            var matching = rowsById.values().stream()
                    .filter(row -> row.datasetId().equals(datasetId)).toList();
            long count = matching.size();
            long sumVersion = matching.stream().mapToLong(DatasetRow::version).sum();
            String maxUpdated = matching.stream().map(DatasetRow::updatedAt)
                    .max(java.util.Comparator.naturalOrder()).map(Object::toString).orElse("-");
            return count + ":" + sumVersion + ":" + maxUpdated;
        }

        @Override
        public Optional<DatasetRow> findByDatasetIdAndBusinessKey(long datasetId, String businessKey) {
            return rowsById.values().stream()
                    .filter(row -> row.datasetId().equals(datasetId) && row.businessKey().equals(businessKey))
                    .findFirst();
        }
    }

    static final class InMemoryDatasetImportRepository implements DatasetImportRepository {
        final List<DatasetImport> saved = new ArrayList<>();

        public void reset() {
            saved.clear();
        }

        @Override
        public DatasetImport save(DatasetImport datasetImport) {
            saved.add(datasetImport);
            return datasetImport;
        }
    }

    /** Zbiór 'licencje' jak w seedzie dev: klucz 'produkt', dwie liczby, uwagi. */
    static Dataset sampleDataset() {
        return new Dataset(1L, "licencje", "Stan licencji", "produkt", true);
    }

    static List<DatasetColumn> sampleColumns() {
        return List.of(
                new DatasetColumn(1L, 1L, "produkt", "Produkt", ColumnType.TEXT, true, false, 10),
                new DatasetColumn(2L, 1L, "posiadane", "Posiadane", ColumnType.NUMBER, true, true, 20),
                new DatasetColumn(3L, 1L, "uzyte", "Użyte", ColumnType.NUMBER, true, true, 30),
                new DatasetColumn(4L, 1L, "uwagi", "Uwagi", ColumnType.TEXT, false, true, 40));
    }
}
