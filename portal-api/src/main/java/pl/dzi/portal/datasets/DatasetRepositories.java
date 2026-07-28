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

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Lean repozytoria modułu (konwencja z tiles/tasks): tylko używane operacje,
 * jawny SQL tam, gdzie jest join, sortowanie albo agregacja.
 */
interface DatasetRepository extends Repository<Dataset, Long> {

    Optional<Dataset> findByCode(String code);

    /** Kafelek REPORT wskazujący ten zbiór — uprawnienia do zbioru idą PRZEZ kafelek. */
    @Query("""
            SELECT t.code FROM tiles t
            WHERE t.action_ref = :datasetCode AND t.tile_type = 'REPORT' AND t.active = 1
            """)
    Optional<String> findTileCodeFor(@Param("datasetCode") String datasetCode);
}

interface DatasetColumnRepository extends Repository<DatasetColumn, Long> {

    @Query("SELECT * FROM dataset_columns WHERE dataset_id = :datasetId ORDER BY col_order, code")
    List<DatasetColumn> findByDatasetIdOrdered(@Param("datasetId") long datasetId);
}

interface DatasetRowRepository extends Repository<DatasetRow, Long> {

    DatasetRow save(DatasetRow row);

    Optional<DatasetRow> findById(Long id);

    List<DatasetRow> findByDatasetIdOrderByBusinessKey(long datasetId);

    Optional<DatasetRow> findByDatasetIdAndBusinessKey(long datasetId, String businessKey);

    @Query("SELECT COUNT(*) FROM dataset_rows WHERE dataset_id = :datasetId")
    long countByDatasetId(@Param("datasetId") long datasetId);

    /**
     * Licznik "w bazie, a nie w pliku" liczony PO STRONIE BAZY (poprawka z przeglądu,
     * commit 31 — wcześniej ładowaliśmy wszystkie agregaty z komórkami tylko po to,
     * żeby je policzyć). :keys lowercase; NIGDY pusta kolekcja — fasada gwarantuje.
     */
    @Query("""
            SELECT COUNT(*) FROM dataset_rows
            WHERE dataset_id = :datasetId AND LOWER(business_key) NOT IN (:keys)
            """)
    long countMissingKeys(@Param("datasetId") long datasetId, @Param("keys") Collection<String> keys);

    /**
     * Token stanu zbioru pod ETag (Etap 6): liczba wierszy + suma wersji + max czasu zmiany.
     * Każda operacja zmienia którąś składową (insert: count, edycja: sum(version), merge: obie),
     * a koszt to JEDNA agregacja po indeksie — dokładnie tyle, ile ma kosztować odpowiedź 304.
     * Świadomie bez kolumny/triggera z licznikiem: token wyliczany, zero stanu do psucia.
     */
    @Query("""
            SELECT CONCAT(COUNT(*), ':', COALESCE(SUM(CAST(version AS BIGINT)), 0), ':',
                          COALESCE(CONVERT(VARCHAR(33), MAX(updated_at), 126), '-'))
            FROM dataset_rows
            WHERE dataset_id = :datasetId
            """)
    String stateToken(@Param("datasetId") long datasetId);
}

interface DatasetImportRepository extends Repository<DatasetImport, Long> {

    DatasetImport save(DatasetImport datasetImport);
}
