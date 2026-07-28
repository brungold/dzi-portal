-- =====================================================================
-- DEV SEED (Etap 3). Ta lokalizacja (db/migration-dev) jest doklejana do
-- spring.flyway.locations WYŁĄCZNIE w profilu dev — prod jej nie widzi.
-- Numeracja od V100, żeby seedy zawsze sortowały się za migracjami schematu.
--
-- Zestaw celowo pokrywa wszystkie przypadki RBAC z portal-bootstrap.js:
--   tester (Admin + Raporty-Odczyt): widzi 4 kafelki, etl-restart z canExecute=true
--   viewer (Raporty-Odczyt):         widzi 3 kafelki, etl-restart z canExecute=false,
--                                    admin-tylko w ogóle niewidoczny
-- W prod kafelki na razie zakłada administrator INSERT-ami wg tego wzoru
-- (panel CRUD admina — backlog).
-- =====================================================================

INSERT INTO tiles (code, name, description, icon, tile_type, action_ref, active, display_order) VALUES
    ('etl-restart',     N'Restart ETL',        N'Ponowne uruchomienie nocnego zasilania ETL', 'refresh',  'SCRIPT', 'etl-restart',                1, 10),
    ('raport-licencje', N'Raport licencji',    N'Aktualny stan wykorzystania licencji',       'chart',    'REPORT', 'licencje',                   1, 20),
    ('intranet',        N'Intranet',           N'Strona intranetowa departamentu',            'link',     'LINK',   'https://intranet.dzi.pl',  1, 30),
    ('admin-tylko',     N'Panel administratora', N'Widoczny wyłącznie dla DZI-Portal-Admin','settings', 'LINK',   'https://intranet.dzi.pl/admin', 1, 40);

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
