-- DEV SEED (Etap 5): zbiór 'licencje' pod kafelek raport-licencje (action_ref='licencje').
-- Uprawnienia idą PRZEZ kafelek: READ = podgląd, EDIT = edycja komórek i import.
-- V100 dawał adminowi tylko READ — tu podnosimy do EDIT, żeby demo edycji działało.

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
