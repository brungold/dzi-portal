-- =====================================================================
-- Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
-- Autor: Maciej Myśliwiec, 2026.
-- =====================================================================
-- V9: wiersze zbiorów danych — agregat Data JDBC z optimistic lockiem.
-- =====================================================================

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
