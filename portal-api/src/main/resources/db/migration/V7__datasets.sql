-- =====================================================================
-- Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
-- Autor: Maciej Myśliwiec, 2026.
-- =====================================================================
-- V7: zbiory danych (datasets) pod kafelki typu REPORT — definicja zbioru.
-- Model rodziny datasets (V7-V11): definicja + typowane kolumny + wiersze-
-- agregaty z komórkami (EAV per komórka). Świadomie BEZ kolumny JSON:
-- komórki jako wiersze dają proste zapytania, naturalny merge per komórka
-- i agregat Data JDBC z @Version (patrz DatasetRow). Wartości kanonicznie
-- jako tekst; typ (dataset_columns.data_type) steruje walidacją
-- i formatowaniem na brzegach (import, edycja, UI).
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
