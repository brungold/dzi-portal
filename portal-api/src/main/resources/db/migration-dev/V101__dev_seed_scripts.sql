-- =====================================================================
-- DEV SEED (Etap 4): whitelist skryptów demo + kafelki pokazujące
-- wszystkie trzy zakończenia (SUCCEEDED / FAILED / TIMED_OUT).
--
-- ŚCIEŻKI: w dev względne do katalogu roboczego workera (moduł portal-worker
-- przy mvn spring-boot:run) -> ../scripts/demo/... . W PROD ścieżki ZAWSZE
-- absolutne (D:\portal\scripts\...) — whitelist nie może zależeć od cwd usługi.
-- =====================================================================

INSERT INTO scripts (code, path, script_type, timeout_seconds, active) VALUES
    ('etl-restart',  '../scripts/demo/demo-success.ps1', 'PS1', 300, 1),
    ('demo-blad',    '../scripts/demo/demo-fail.ps1',    'PS1', 300, 1),
    ('demo-timeout', '../scripts/demo/demo-timeout.ps1', 'PS1', 5,   1);

INSERT INTO tiles (code, name, description, icon, tile_type, action_ref, active, display_order) VALUES
    ('demo-blad',    N'Demo: błąd skryptu',  N'Skrypt kończy się kodem 3 i pisze na stderr', 'bug',   'SCRIPT', 'demo-blad',    1, 50),
    ('demo-timeout', N'Demo: timeout',       N'Skrypt śpi 30 s przy limicie 5 s',            'clock', 'SCRIPT', 'demo-timeout', 1, 60);

INSERT INTO tile_permissions (tile_id, ad_group, permission_level)
SELECT id, 'DZI-Portal-Admin', 'EXECUTE' FROM tiles WHERE code = 'demo-blad';
INSERT INTO tile_permissions (tile_id, ad_group, permission_level)
SELECT id, 'DZI-Portal-Admin', 'EXECUTE' FROM tiles WHERE code = 'demo-timeout';
