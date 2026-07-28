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

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * XLSX -> staging w pamięci. Pierwszy arkusz, pierwszy wiersz = nagłówki dopasowywane
 * do kolumn zbioru po LABEL albo CODE (bez rozróżniania wielkości liter i spacji).
 * Kolumny nieznane -> ostrzeżenie (ignorowane); brak kolumny wymaganej -> odrzucenie pliku.
 *
 * Staging w pamięci świadomie: pliki portalowe to setki-tysiące wierszy.
 * Próg rewizji (staging w tabeli + strumieniowy SAX): >50 tys. wierszy.
 */
class XlsxParser {

    /** Wiersz po walidacji: klucz biznesowy + wartości KANONICZNE per kod kolumny. */
    record StagedRow(int excelRowNumber, String businessKey, Map<String, String> cellValues) {
    }

    record ParseError(int excelRowNumber, String column, String message) {
        String describe() {
            String where = excelRowNumber > 0 ? "wiersz " + excelRowNumber : "plik";
            String col = column != null ? ", kolumna '" + column + "'" : "";
            return where + col + ": " + message;
        }
    }

    record ParseResult(List<StagedRow> rows, List<ParseError> errors, List<String> warnings) {
        boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    ParseResult parse(InputStream inputStream, List<DatasetColumn> columns, String keyColumnCode) throws IOException {
        List<ParseError> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<StagedRow> staged = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            DataFormatter formatter = new DataFormatter(new Locale("pl", "PL"));

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                errors.add(new ParseError(0, null, "pusty arkusz — brak wiersza nagłówków"));
                return new ParseResult(staged, errors, warnings);
            }

            Map<Integer, DatasetColumn> columnByIndex = mapHeader(headerRow, columns, warnings, formatter);
            for (DatasetColumn column : columns) {
                if (column.required() && !columnByIndex.containsValue(column)) {
                    errors.add(new ParseError(0, column.label(),
                            "brak wymaganej kolumny w nagłówku pliku"));
                }
            }
            if (!errors.isEmpty()) {
                return new ParseResult(staged, errors, warnings);
            }

            Map<String, Integer> seenKeys = new HashMap<>();
            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                int excelRowNumber = rowIndex + 1;
                if (row == null || isBlank(row, columnByIndex, formatter, evaluator)) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (Map.Entry<Integer, DatasetColumn> entry : columnByIndex.entrySet()) {
                    DatasetColumn column = entry.getValue();
                    String raw = readCell(row.getCell(entry.getKey()), column.dataType(), formatter, evaluator);
                    try {
                        String canonical = raw == null ? null : column.dataType().canonicalize(raw);
                        if (canonical == null && column.required()) {
                            errors.add(new ParseError(excelRowNumber, column.label(), "wartość wymagana jest pusta"));
                        }
                        values.put(column.code(), canonical);
                    } catch (IllegalArgumentException e) {
                        errors.add(new ParseError(excelRowNumber, column.label(), e.getMessage()));
                    }
                }
                String businessKey = values.get(keyColumnCode);
                if (businessKey == null) {
                    continue; // błąd pustego klucza zgłosiła już walidacja required
                }
                Integer duplicateOf = seenKeys.putIfAbsent(businessKey.toLowerCase(Locale.ROOT), excelRowNumber);
                if (duplicateOf != null) {
                    errors.add(new ParseError(excelRowNumber, null,
                            "zduplikowany klucz '" + businessKey + "' (pierwsze wystąpienie: wiersz " + duplicateOf + ")"));
                    continue;
                }
                staged.add(new StagedRow(excelRowNumber, businessKey, values));
            }
        }
        return new ParseResult(staged, errors, warnings);
    }

    private static Map<Integer, DatasetColumn> mapHeader(Row headerRow, List<DatasetColumn> columns,
                                                         List<String> warnings, DataFormatter formatter) {
        Map<String, DatasetColumn> byNormalizedName = new HashMap<>();
        for (DatasetColumn column : columns) {
            byNormalizedName.put(normalize(column.label()), column);
            byNormalizedName.put(normalize(column.code()), column);
        }
        Map<Integer, DatasetColumn> result = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String header = formatter.formatCellValue(cell).trim();
            if (header.isEmpty()) {
                continue;
            }
            DatasetColumn column = byNormalizedName.get(normalize(header));
            if (column == null) {
                warnings.add("kolumna '" + header + "' z pliku została zignorowana (brak w definicji zbioru)");
            } else {
                result.put(cell.getColumnIndex(), column);
            }
        }
        return result;
    }

    /**
     * Surowa wartość komórki jako tekst do kanonizacji. Liczby czytamy z wartości
     * numerycznej (nie sformatowanej — "1 200,50" po polsku zaokrągla i myli),
     * daty przez DateUtil, resztę przez DataFormatter (obsługuje też FORMULA po ewaluacji).
     */
    private static String readCell(Cell cell, ColumnType type, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return null;
        }
        CellType effectiveType = cell.getCellType() == CellType.FORMULA
                ? evaluator.evaluateFormulaCell(cell)
                : cell.getCellType();

        if (effectiveType == CellType.NUMERIC) {
            if (type == ColumnType.DATE && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate().toString();
            }
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        if (effectiveType == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        }
        String text = formatter.formatCellValue(cell, evaluator).trim();
        return text.isEmpty() ? null : text;
    }

    private static boolean isBlank(Row row, Map<Integer, DatasetColumn> columnByIndex,
                                   DataFormatter formatter, FormulaEvaluator evaluator) {
        return columnByIndex.keySet().stream()
                .allMatch(index -> {
                    Cell cell = row.getCell(index);
                    return cell == null || formatter.formatCellValue(cell, evaluator).isBlank();
                });
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace(" ", "");
    }
}
