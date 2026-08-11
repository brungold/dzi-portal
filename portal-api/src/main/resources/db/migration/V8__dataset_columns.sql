-- =====================================================================
-- Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
-- Autor: Maciej Myśliwiec, 2026.
-- =====================================================================
-- V8: kolumny zbiorów danych (typ, wymagalność, edytowalność, kolejność).
-- =====================================================================

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
