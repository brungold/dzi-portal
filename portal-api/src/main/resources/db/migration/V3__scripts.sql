-- =====================================================================
-- Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
-- Autor: Maciej Myśliwiec, 2026.
-- =====================================================================
-- V3: whitelist skryptów uruchamialnych z kafelków typu SCRIPT.
-- =====================================================================

CREATE TABLE scripts (
    id              BIGINT IDENTITY(1,1) NOT NULL,
    code            NVARCHAR(50)  NOT NULL,
    path            NVARCHAR(500) NOT NULL,   -- wyłącznie z katalogu skryptów; nigdy z żądania
    script_type     VARCHAR(10)   NOT NULL,
    params_schema   NVARCHAR(MAX) NULL,       -- JSON Schema parametrów (walidacja w API)
    timeout_seconds INT           NOT NULL CONSTRAINT df_scripts_timeout DEFAULT 300,
    active          BIT           NOT NULL CONSTRAINT df_scripts_active DEFAULT 1,
    CONSTRAINT pk_scripts PRIMARY KEY (id),
    CONSTRAINT uq_scripts_code UNIQUE (code),
    CONSTRAINT ck_scripts_type CHECK (script_type IN ('PS1','BAT','EXE','JAR'))
);
