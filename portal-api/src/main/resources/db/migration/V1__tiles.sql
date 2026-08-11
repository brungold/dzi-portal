-- =====================================================================
-- Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
-- Autor: Maciej Myśliwiec, 2026.
-- =====================================================================
-- V1: kafelki — pozycje widoczne na portalu.
-- Konwencje schematu: nazwy angielskie snake_case, czas w UTC (DATETIME2).
-- Układ migracji od 2026-08 (konsolidacja): jeden plik = jedna tabela,
-- w postaci DOCELOWEJ (naniesione zmiany z dawnych V1-V6).
-- =====================================================================

CREATE TABLE tiles (
    id            BIGINT IDENTITY(1,1) NOT NULL,
    code          NVARCHAR(50)   NOT NULL,  -- stały identyfikator; frontend: data-tile-id
    name          NVARCHAR(200)  NOT NULL,
    description   NVARCHAR(1000) NULL,
    icon          NVARCHAR(50)   NULL,
    tile_type     VARCHAR(20)    NOT NULL,
    action_ref    NVARCHAR(400)  NULL,      -- SCRIPT: scripts.code | REPORT: datasets.code | LINK: URL
    active        BIT            NOT NULL CONSTRAINT df_tiles_active DEFAULT 1,
    display_order INT            NOT NULL CONSTRAINT df_tiles_order DEFAULT 100,
    CONSTRAINT pk_tiles PRIMARY KEY (id),
    CONSTRAINT uq_tiles_code UNIQUE (code),
    CONSTRAINT ck_tiles_type CHECK (tile_type IN ('SCRIPT','REPORT','LINK'))
);
