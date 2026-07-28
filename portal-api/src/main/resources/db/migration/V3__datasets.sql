-- =====================================================================
-- V3 (Etap 5): zbiory danych (datasets) pod kafelki typu REPORT.
-- Model: definicja + typowane kolumny + wiersze-agregaty z komórkami (EAV per komórka).
-- Świadomie BEZ kolumny JSON: komórki jako wiersze dają proste zapytania,
-- naturalny merge per komórka i agregat Data JDBC z @Version (patrz DatasetRow).
-- Wartości kanonicznie jako tekst; typ (dataset_columns.data_type) steruje
-- walidacją i formatowaniem na brzegach (import, edycja, UI).
-- =====================================================================

CREATE TABLE datasets (
    id         BIGINT IDENTITY(1,1) NOT NULL,
    code       NVARCHAR(50)  NOT NULL,   -- tiles.action_ref (tile_type=REPORT) wskazuje ten kod
    name       NVARCHAR(200) NOT NULL,
    key_column NVARCHAR(50)  NOT NULL,   -- code kolumny będącej kluczem biznesowym wierszy
    active     BIT           NOT NULL CONSTRAINT df_datasets_active DEFAULT 1,
    CONSTRAINT pk_datasets PRIMARY KEY (id),
    CONSTRAINT uq_datasets_code UNIQUE (code)
);

CREATE TABLE dataset_columns (
    id         BIGINT IDENTITY(1,1) NOT NULL,
    dataset_id BIGINT        NOT NULL,
    code       NVARCHAR(50)  NOT NULL,
    label      NVARCHAR(200) NOT NULL,   -- nagłówek w XLSX i w Tabulatorze
    data_type  VARCHAR(10)   NOT NULL,
    required   BIT           NOT NULL CONSTRAINT df_dataset_columns_req DEFAULT 0,
    editable   BIT           NOT NULL CONSTRAINT df_dataset_columns_edit DEFAULT 1,
    col_order  INT           NOT NULL CONSTRAINT df_dataset_columns_order DEFAULT 100,
    CONSTRAINT pk_dataset_columns PRIMARY KEY (id),
    CONSTRAINT fk_dataset_columns_dataset FOREIGN KEY (dataset_id) REFERENCES datasets (id),
    CONSTRAINT uq_dataset_columns UNIQUE (dataset_id, code),
    CONSTRAINT ck_dataset_columns_type CHECK (data_type IN ('TEXT','NUMBER','DATE','BOOL'))
);

CREATE TABLE dataset_rows (
    id           BIGINT IDENTITY(1,1) NOT NULL,
    dataset_id   BIGINT        NOT NULL,
    business_key NVARCHAR(200) NOT NULL,
    updated_by   NVARCHAR(100) NOT NULL,
    updated_at   DATETIME2     NOT NULL,
    version      INT           NOT NULL CONSTRAINT df_dataset_rows_version DEFAULT 0,  -- optimistic lock (@Version)
    CONSTRAINT pk_dataset_rows PRIMARY KEY (id),
    CONSTRAINT fk_dataset_rows_dataset FOREIGN KEY (dataset_id) REFERENCES datasets (id),
    CONSTRAINT uq_dataset_rows UNIQUE (dataset_id, business_key)
);

-- Komórki = dzieci agregatu DatasetRow (Spring Data JDBC przy save robi delete+insert dzieci,
-- stąd brak własnego IDENTITY — klucz naturalny (row_id, column_code) wystarcza).
CREATE TABLE dataset_cells (
    row_id      BIGINT         NOT NULL,
    column_code NVARCHAR(50)   NOT NULL,
    cell_value  NVARCHAR(4000) NULL,
    CONSTRAINT pk_dataset_cells PRIMARY KEY (row_id, column_code),
    CONSTRAINT fk_dataset_cells_row FOREIGN KEY (row_id) REFERENCES dataset_rows (id) ON DELETE CASCADE
);

-- Historia importów: co, kto, kiedy i z jakim wynikiem (raport błędów dla odrzuconych).
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
