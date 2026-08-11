-- =====================================================================
-- Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
-- Autor: Maciej Myśliwiec, 2026.
-- =====================================================================
-- V11: historia importów XLSX: co, kto, kiedy i z jakim wynikiem
-- (raport błędów dla odrzuconych).
-- =====================================================================

CREATE TABLE dataset_imports (
    id             BIGINT IDENTITY(1,1) NOT NULL,
    dataset_id     BIGINT        NOT NULL,
    filename       NVARCHAR(400) NOT NULL,
    imported_by    NVARCHAR(100) NOT NULL,
    ts_utc         DATETIME2     NOT NULL,
    status         VARCHAR(10)   NOT NULL,
    rows_inserted  INT           NOT NULL CONSTRAINT df_di_ins DEFAULT 0,
    rows_updated   INT           NOT NULL CONSTRAINT df_di_upd DEFAULT 0,
    rows_unchanged INT           NOT NULL CONSTRAINT df_di_unch DEFAULT 0,
    rows_missing   INT           NOT NULL CONSTRAINT df_di_miss DEFAULT 0,  -- w bazie, brak w pliku (informacyjnie)
    error_report   NVARCHAR(MAX) NULL,
    CONSTRAINT pk_dataset_imports PRIMARY KEY (id),
    CONSTRAINT fk_dataset_imports_dataset FOREIGN KEY (dataset_id) REFERENCES datasets (id),
    CONSTRAINT ck_dataset_imports_status CHECK (status IN ('OK','REJECTED'))
);
