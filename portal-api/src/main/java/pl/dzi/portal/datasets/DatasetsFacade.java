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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.dzi.portal.infrastructure.security.PortalUser;
import pl.dzi.portal.tiles.AccessFacade;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Zbiory danych. Uprawnienia idą PRZEZ kafelek REPORT wskazujący zbiór
 * (AccessFacade: READ = podgląd, EDIT = edycja komórek i import) — kontrola
 * siedzi tutaj, nie w @PreAuthorize, bo mapowanie zbiór->kafelek wymaga zapytania.
 *
 * SEMANTYKA IMPORTU (świadome decyzje):
 *  - wszystko albo nic: JAKIKOLWIEK błąd walidacji => 422 z pełnym raportem, zero zapisów,
 *  - plik jest źródłem prawdy dla swoich wierszy: UPSERT po kluczu biznesowym,
 *    ręczne korekty żyją do następnego importu (nadpisze je),
 *  - wierszy nieobecnych w pliku NIE usuwamy — tylko raportujemy (rows_missing),
 *  - merge w JEDNEJ transakcji; równoległa edycja komórki w trakcie => rollback całości
 *    (OptimisticLockingFailureException -> 409, import do powtórzenia).
 */
@Slf4j
@RequiredArgsConstructor
class DatasetsFacade {

    private final DatasetRepository datasets;
    private final DatasetColumnRepository columns;
    private final DatasetRowRepository rows;
    private final DatasetImportRepository imports;
    private final AccessFacade access;
    private final XlsxParser parser;
    private final Clock clock;

    record ColumnView(String code, String label, String dataType, boolean required, boolean editable) {
    }

    record RowView(long id, int version, String businessKey, Map<String, String> data,
                   String updatedBy, Instant updatedAt) {
    }

    record DatasetView(String code, String name, String keyColumn, boolean canEdit,
                       List<ColumnView> columns, List<RowView> rows) {
    }

    record ImportReport(String status, int inserted, int updated, int unchanged, int missingInFile,
                        List<String> errors, List<String> warnings) {
    }

    record EditedCell(long rowId, int version, String columnCode, String value) {
    }

    DatasetView view(String datasetCode, Authentication authentication) {
        Dataset dataset = requirePermission(datasetCode, authentication, false);
        boolean canEdit = access.canEdit(tileCodeOf(dataset), authentication);
        List<ColumnView> columnViews = columns.findByDatasetIdOrdered(dataset.id()).stream()
                .map(column -> new ColumnView(column.code(), column.label(), column.dataType().name(),
                        column.required(), column.editable()))
                .toList();
        List<RowView> rowViews = rows.findByDatasetIdOrderByBusinessKey(dataset.id()).stream()
                .map(row -> new RowView(row.id(), row.version(), row.businessKey(), row.cellValues(),
                        row.updatedBy(), row.updatedAt()))
                .toList();
        return new DatasetView(dataset.code(), dataset.name(), dataset.keyColumn(), canEdit,
                columnViews, rowViews);
    }

    /** Token pod ETag: te same uprawnienia co podgląd (READ przez kafelek). */
    String stateToken(String datasetCode, Authentication authentication) {
        Dataset dataset = requirePermission(datasetCode, authentication, false);
        return rows.stateToken(dataset.id());
    }

    @Transactional
    ImportReport importFile(String datasetCode, String filename, InputStream content,
                            Authentication authentication) throws IOException {
        Dataset dataset = requirePermission(datasetCode, authentication, true);
        String user = loginOf(authentication);
        List<DatasetColumn> columnList = columns.findByDatasetIdOrdered(dataset.id());

        XlsxParser.ParseResult parsed = parser.parse(content, columnList, dataset.keyColumn());
        if (parsed.hasErrors()) {
            List<String> errorLines = parsed.errors().stream().map(XlsxParser.ParseError::describe).toList();
            imports.save(DatasetImport.rejected(dataset.id(), filename, user, clock.instant(),
                    String.join("\n", errorLines)));
            // save importu przeżyje mimo braku merge — celowo: historia odrzuconych też jest audytem.
            return new ImportReport("REJECTED", 0, 0, 0, 0, errorLines, parsed.warnings());
        }

        Instant now = clock.instant();
        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        Set<String> fileKeys = new HashSet<>();

        for (XlsxParser.StagedRow staged : parsed.rows()) {
            fileKeys.add(staged.businessKey().toLowerCase(java.util.Locale.ROOT));
            Optional<DatasetRow> existing = rows.findByDatasetIdAndBusinessKey(dataset.id(), staged.businessKey());
            if (existing.isEmpty()) {
                rows.save(DatasetRow.create(dataset.id(), staged.businessKey(), staged.cellValues(), user, now));
                inserted++;
            } else if (sameValues(existing.get().cellValues(), staged.cellValues())) {
                unchanged++;
            } else {
                rows.save(existing.get().withCells(staged.cellValues(), user, now));
                updated++;
            }
        }

        // Poprawka z przeglądu (commit 31): COUNT po stronie bazy zamiast ładowania
        // wszystkich agregatów z komórkami tylko po to, żeby policzyć wiersze.
        int missing = (int) (fileKeys.isEmpty()
                ? rows.countByDatasetId(dataset.id())
                : rows.countMissingKeys(dataset.id(), fileKeys));

        imports.save(DatasetImport.ok(dataset.id(), filename, user, now, inserted, updated, unchanged, missing));
        log.info("Import '{}' do zbioru {}: +{} ~{} ={} (poza plikiem: {})",
                filename, datasetCode, inserted, updated, unchanged, missing);
        return new ImportReport("OK", inserted, updated, unchanged, missing, List.of(), parsed.warnings());
    }

    EditedCell editCell(String datasetCode, long rowId, String columnCode, String rawValue,
                        int expectedVersion, Authentication authentication) {
        Dataset dataset = requirePermission(datasetCode, authentication, true);
        DatasetColumn column = columns.findByDatasetIdOrdered(dataset.id()).stream()
                .filter(candidate -> candidate.code().equals(columnCode))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Nieznana kolumna: " + columnCode));
        if (!column.editable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Kolumna '" + column.label() + "' nie podlega edycji");
        }

        DatasetRow row = rows.findById(rowId)
                .filter(candidate -> candidate.datasetId().equals(dataset.id()))
                .orElseThrow(() -> new AccessDeniedException("Brak dostępu do wiersza"));
        if (row.version() != expectedVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Wiersz został w międzyczasie zmieniony (wersja " + row.version()
                            + ", edytował: " + row.updatedBy() + ") — odśwież dane");
        }

        String canonical;
        try {
            canonical = rawValue == null ? null : column.dataType().canonicalize(rawValue);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wartość odrzucona: " + e.getMessage());
        }
        if (canonical == null && column.required()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Kolumna '" + column.label() + "' jest wymagana");
        }

        // save() z @Version: przegrany wyścig (ktoś zapisał między naszym odczytem a zapisem)
        // kończy się OptimisticLockingFailureException -> 409 z GlobalExceptionHandler.
        DatasetRow saved = rows.save(row.withCellValue(columnCode, canonical, loginOf(authentication),
                clock.instant()));
        return new EditedCell(saved.id(), saved.version(), columnCode, canonical);
    }

    private Dataset requirePermission(String datasetCode, Authentication authentication, boolean edit) {
        Dataset dataset = datasets.findByCode(datasetCode)
                .filter(Dataset::active)
                .orElseThrow(() -> new AccessDeniedException("Brak dostępu do zbioru"));
        String tileCode = tileCodeOf(dataset);
        boolean allowed = edit ? access.canEdit(tileCode, authentication) : access.canRead(tileCode, authentication);
        if (!allowed) {
            throw new AccessDeniedException("Brak dostępu do zbioru");
        }
        return dataset;
    }

    /** Zbiór bez kafelka REPORT = niewystawiony => dostęp zawsze odmówiony (kod '!' nie istnieje w tiles). */
    private String tileCodeOf(Dataset dataset) {
        return datasets.findTileCodeFor(dataset.code()).orElse("!brak-kafelka");
    }

    private static boolean sameValues(Map<String, String> current, Map<String, String> incoming) {
        if (!current.keySet().equals(incoming.keySet())) {
            return false;
        }
        return incoming.entrySet().stream()
                .allMatch(entry -> Objects.equals(current.get(entry.getKey()), entry.getValue()));
    }

    private static String loginOf(Authentication authentication) {
        return ((PortalUser) authentication.getPrincipal()).login();
    }
}
