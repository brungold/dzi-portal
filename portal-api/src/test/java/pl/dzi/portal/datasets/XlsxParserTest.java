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

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.dzi.portal.datasets.InMemoryDatasetRepositories.sampleColumns;
import static pl.dzi.portal.datasets.TestWorkbooks.xlsx;

class XlsxParserTest {

    private final XlsxParser parser = new XlsxParser();
    private static final String[] HEADER = {"Produkt", "Posiadane", "Użyte", "Uwagi"};

    @Test
    void should_stage_rows_with_canonical_values() throws IOException {
        var result = parser.parse(xlsx(HEADER,
                        new Object[]{"Microsoft 365 E3", 1200, 1140, "po inwentaryzacji"},
                        new Object[]{"Acrobat Pro", "1 200,5", 33, null}),   // liczba po polsku, tekstem
                sampleColumns(), "produkt");

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().get(0).businessKey()).isEqualTo("Microsoft 365 E3");
        assertThat(result.rows().get(0).cellValues().get("posiadane")).isEqualTo("1200"); // NUMERIC bez .0
        assertThat(result.rows().get(1).cellValues().get("posiadane")).isEqualTo("1200.5"); // przecinek->kropka
        assertThat(result.rows().get(1).cellValues().get("uwagi")).isNull();
    }

    @Test
    void should_report_row_errors_with_excel_row_numbers() throws IOException {
        var result = parser.parse(xlsx(HEADER,
                        new Object[]{"Produkt A", "abc", 1, null},   // wiersz 2: zła liczba
                        new Object[]{null, 5, 1, null}),             // wiersz 3: pusty klucz wymagany
                sampleColumns(), "produkt");

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors()).extracting(XlsxParser.ParseError::excelRowNumber).contains(2, 3);
        assertThat(result.errors().get(0).describe()).contains("wiersz 2").contains("nie jest liczbą");
    }

    @Test
    void should_reject_file_missing_required_column() throws IOException {
        var result = parser.parse(xlsx(new String[]{"Produkt", "Uwagi"},
                        new Object[]{"Produkt A", "x"}),
                sampleColumns(), "produkt");

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors()).anyMatch(error ->
                error.describe().contains("brak wymaganej kolumny"));
    }

    @Test
    void should_detect_duplicate_business_keys_case_insensitively() throws IOException {
        var result = parser.parse(xlsx(HEADER,
                        new Object[]{"Acrobat Pro", 1, 1, null},
                        new Object[]{"ACROBAT PRO", 2, 2, null}),
                sampleColumns(), "produkt");

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors().get(0).describe()).contains("zduplikowany klucz");
    }

    @Test
    void should_warn_about_unknown_columns_and_skip_blank_rows() throws IOException {
        var result = parser.parse(xlsx(new String[]{"Produkt", "Posiadane", "Użyte", "Kolumna widmo"},
                        new Object[]{"Produkt A", 1, 1, "ignorowane"},
                        new Object[]{null, null, null, null}),       // pusty wiersz
                sampleColumns(), "produkt");

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.rows()).hasSize(1);
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("Kolumna widmo"));
    }

    @Test
    void should_match_headers_by_code_or_label_ignoring_case() throws IOException {
        var result = parser.parse(xlsx(new String[]{"PRODUKT", "posiadane", "Użyte"},
                        new Object[]{"Produkt A", 1, 2}),
                sampleColumns(), "produkt");

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.rows().get(0).cellValues())
                .containsEntry("posiadane", "1")
                .containsEntry("uzyte", "2");
    }
}
