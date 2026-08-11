-- =====================================================================
-- Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
-- Autor: Maciej Myśliwiec, 2026.
-- =====================================================================
-- DEV SEED (migracja repeatable). Ta lokalizacja (db/migration-dev) jest
-- doklejana do spring.flyway.locations WYŁĄCZNIE w profilu dev — prod jej
-- nie widzi. Migracje R__ nie mają numerów i wykonują się zawsze PO
-- wersjonowanych — znika konflikt numeracji, na który wpadliśmy 2026-08
-- (dawne V100-102 blokowały dokładanie migracji schematu poniżej V100).
--
-- Idempotencja: każda sekcja ma strażnika IF NOT EXISTS i wykonuje się
-- tylko na bazie bez seedu. Po edycji tego pliku odbuduj bazę dev (DROP)
-- — to szybsze i pewniejsze niż sprzątanie danych powiązanych FK.
--
-- Zestaw celowo pokrywa wszystkie przypadki RBAC z portal-bootstrap.js:
--   tester (Admin + Raporty-Odczyt): widzi 4 kafelki, etl-restart z canExecute=true
--   viewer (Raporty-Odczyt):         widzi 3 kafelki, etl-restart z canExecute=false,
--                                    admin-tylko w ogóle niewidoczny
-- =====================================================================

-- ---------- Sekcja 1: kafelki + uprawnienia (dawne V100) ----------
IF NOT EXISTS (SELECT 1 FROM tiles WHERE code = 'etl-restart')
BEGIN
    INSERT INTO tiles (code, name, description, icon, tile_type, action_ref, active, display_order) VALUES
        ('etl-restart',     N'Restart ETL',          N'Ponowne uruchomienie nocnego zasilania ETL', 'refresh',  'SCRIPT', 'etl-restart',                   1, 10),
        ('raport-licencje', N'Raport licencji',      N'Aktualny stan wykorzystania licencji',       'chart',    'REPORT', 'licencje',                      1, 20),
        ('intranet',        N'Intranet',             N'Strona intranetowa departamentu',            'link',     'LINK',   'https://intranet.dzi.pl',       1, 30),
        ('admin-tylko',     N'Panel administratora', N'Widoczny wyłącznie dla DZI-Portal-Admin',    'settings', 'LINK',   'https://intranet.dzi.pl/admin', 1, 40);

    INSERT INTO tile_permissions (tile_id, ad_group, permission_level)
    SELECT id, 'DZI-Portal-Admin', 'EXECUTE' FROM tiles WHERE code = 'etl-restart';
    INSERT INTO tile_permissions (tile_id, ad_group, permission_level)
    SELECT id, 'DZI-Portal-Raporty-Odczyt', 'READ' FROM tiles WHERE code = 'etl-restart';

    INSERT INTO tile_permissions (tile_id, ad_group, permission_level)
    SELECT id, 'DZI-Portal-Admin', 'READ' FROM tiles WHERE code = 'raport-licencje';
    INSERT INTO tile_permissions (tile_id, ad_group, permission_level)
    SELECT id, 'DZI-Portal-Raporty-Odczyt', 'READ' FROM tiles WHERE code = 'raport-licencje';

    INSERT INTO tile_permissions (tile_id, ad_group, permission_level)
    SELECT id, 'DZI-Portal-Admin', 'READ' FROM tiles WHERE code = 'intranet';
    INSERT INTO tile_permissions (tile_id, ad_group, permission_level)
    SELECT id, 'DZI-Portal-Raporty-Odczyt', 'READ' FROM tiles WHERE code = 'intranet';

    INSERT INTO tile_permissions (tile_id, ad_group, permission_level)
    SELECT id, 'DZI-Portal-Admin', 'READ' FROM tiles WHERE code = 'admin-tylko';
END;

-- ---------- Sekcja 2: whitelist skryptów demo (dawne V101) ----------
-- ŚCIEŻKI: w dev względne do katalogu roboczego workera (moduł portal-worker
-- przy mvn spring-boot:run) -> ../scripts/demo/... . W PROD ścieżki ZAWSZE
-- absolutne (D:\portal\scripts\...) — whitelist nie może zależeć od cwd usługi.
IF NOT EXISTS (SELECT 1 FROM scripts WHERE code = 'etl-restart')
BEGIN
    INSERT INTO scripts (code, path, script_type, timeout_seconds, active) VALUES
        ('etl-restart',  '../scripts/demo/demo-success.ps1', 'PS1', 300, 1),
        ('demo-blad',    '../scripts/demo/demo-fail.ps1',    'PS1', 300, 1),
        ('demo-timeout', '../scripts/demo/demo-timeout.ps1', 'PS1', 5,   1);

    INSERT INTO tiles (code, name, description, icon, tile_type, action_ref, active, display_order) VALUES
        ('demo-blad',    N'Demo: błąd skryptu', N'Skrypt kończy się kodem 3 i pisze na stderr', 'bug',   'SCRIPT', 'demo-blad',    1, 50),
        ('demo-timeout', N'Demo: timeout',      N'Skrypt śpi 30 s przy limicie 5 s',            'clock', 'SCRIPT', 'demo-timeout', 1, 60);

    INSERT INTO tile_permissions (tile_id, ad_group, permission_level)
    SELECT id, 'DZI-Portal-Admin', 'EXECUTE' FROM tiles WHERE code = 'demo-blad';
    INSERT INTO tile_permissions (tile_id, ad_group, permission_level)
    SELECT id, 'DZI-Portal-Admin', 'EXECUTE' FROM tiles WHERE code = 'demo-timeout';
END;

-- ---------- Sekcja 3: zbiór 'licencje' pod kafelek raportu (dawne V102) ----------
-- Uprawnienia idą PRZEZ kafelek: READ = podgląd, EDIT = edycja komórek i import.
-- Sekcja 1 dała adminowi READ — tu podnosimy do EDIT, żeby demo edycji działało.
IF NOT EXISTS (SELECT 1 FROM datasets WHERE code = 'licencje')
BEGIN
    INSERT INTO datasets (code, name, key_column, active) VALUES
        ('licencje', N'Stan licencji oprogramowania', 'produkt', 1);

    INSERT INTO dataset_columns (dataset_id, code, label, data_type, required, editable, col_order)
    SELECT id, 'produkt',   N'Produkt',   'TEXT',   1, 0, 10 FROM datasets WHERE code = 'licencje';
    INSERT INTO dataset_columns (dataset_id, code, label, data_type, required, editable, col_order)
    SELECT id, 'posiadane', N'Posiadane', 'NUMBER', 1, 1, 20 FROM datasets WHERE code = 'licencje';
    INSERT INTO dataset_columns (dataset_id, code, label, data_type, required, editable, col_order)
    SELECT id, 'uzyte',     N'Użyte',     'NUMBER', 1, 1, 30 FROM datasets WHERE code = 'licencje';
    INSERT INTO dataset_columns (dataset_id, code, label, data_type, required, editable, col_order)
    SELECT id, 'uwagi',     N'Uwagi',     'TEXT',   0, 1, 40 FROM datasets WHERE code = 'licencje';

    -- Dwa wiersze startowe (plik docs/przyklady/licencje-import.xlsx je nadpisze i doda trzy nowe)
    INSERT INTO dataset_rows (dataset_id, business_key, updated_by, updated_at)
    SELECT id, N'Microsoft 365 E3', 'seed', SYSUTCDATETIME() FROM datasets WHERE code = 'licencje';
    INSERT INTO dataset_cells (row_id, column_code, cell_value)
    SELECT r.id, v.code, v.val
    FROM dataset_rows r
    JOIN datasets d ON d.id = r.dataset_id AND d.code = 'licencje'
    CROSS APPLY (VALUES ('produkt', N'Microsoft 365 E3'), ('posiadane', N'1200'), ('uzyte', N'1085'), ('uwagi', NULL)) v(code, val)
    WHERE r.business_key = N'Microsoft 365 E3';

    INSERT INTO dataset_rows (dataset_id, business_key, updated_by, updated_at)
    SELECT id, N'Copilot Studio', 'seed', SYSUTCDATETIME() FROM datasets WHERE code = 'licencje';
    INSERT INTO dataset_cells (row_id, column_code, cell_value)
    SELECT r.id, v.code, v.val
    FROM dataset_rows r
    JOIN datasets d ON d.id = r.dataset_id AND d.code = 'licencje'
    CROSS APPLY (VALUES ('produkt', N'Copilot Studio'), ('posiadane', N'25'), ('uzyte', N'11'), ('uwagi', N'pilotaż DZI')) v(code, val)
    WHERE r.business_key = N'Copilot Studio';

    -- Edycja/import wymagają EDIT na kafelku raportu
    INSERT INTO tile_permissions (tile_id, ad_group, permission_level)
    SELECT id, 'DZI-Portal-Admin', 'EDIT' FROM tiles WHERE code = 'raport-licencje';
END;
