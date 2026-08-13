-- ============================================================================
-- Portal DZI — szablon rejestracji NOWEGO modułu za strażnikiem (ADR-0007).
-- Autor: Maciej Myśliwiec, 2026. Art. 16 pr. aut. — szczegóły: AUTORSTWO.md.
--
-- Warunek konieczny: <code> = nazwa podkatalogu w D:\portal\apps\ (co do znaku).
-- Format loginów: potwierdzać z /api/whoami albo v_audit_log_pl — w projekcie
-- występują OBA warianty (zwonik.piotr vs maciej.mysliwiec); literówka = 403.
-- Uruchomienie: sqlcmd -S localhost -d portal -E -f 65001 -i apps-nowy-modul.sql
-- ============================================================================

INSERT INTO tiles (code, name, description, tile_type, action_ref, active, display_order)
VALUES (N'<code>', N'<Nazwa na kafelku>',
        N'<Opis pod nazwą>',
        'LINK', N'/apps/<code>/', 1, 100);

-- Uprawnienia: login / departament (np. 'dzi') / 'wszyscy'
INSERT INTO tile_permissions (tile_id, ad_group, permission_level)
SELECT t.id, v.grupa, 'READ'
FROM tiles t
CROSS JOIN (VALUES (N'<login-albo-dept-albo-wszyscy>')) v(grupa)
WHERE t.code = N'<code>';

-- Kontrola:
-- SELECT t.code, p.ad_group, p.permission_level
-- FROM tiles t LEFT JOIN tile_permissions p ON p.tile_id = t.id
-- WHERE t.code = N'<code>';
