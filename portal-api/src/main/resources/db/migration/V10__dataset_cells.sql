-- =====================================================================
-- Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
-- Autor: Maciej Myśliwiec, 2026.
-- =====================================================================
-- V10: komórki wierszy — dzieci agregatu DatasetRow (Spring Data JDBC
-- przy save robi delete+insert dzieci, stąd brak własnego IDENTITY —
-- klucz naturalny (row_id, column_code) wystarcza).
-- =====================================================================

CREATE TABLE dataset_cells (
    row_id      BIGINT         NOT NULL,
    column_code NVARCHAR(50)   NOT NULL,
    cell_value  NVARCHAR(4000) NULL,
    CONSTRAINT pk_dataset_cells PRIMARY KEY (row_id, column_code),
    CONSTRAINT fk_dataset_cells_row FOREIGN KEY (row_id) REFERENCES dataset_rows (id) ON DELETE CASCADE
);
