-- ======================================================================
-- Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
-- Autor: Maciej Myśliwiec, 2026.
-- ======================================================================
-- Rejestracja kafelka panelu administracyjnego (ADR-0009, Faza A — odczyt).
-- Uruchomienie [SERWER #2]:  sqlcmd -S localhost -d portal -E -f 65001 -i apps-administracja.sql
-- Warunek: pliki modułu leżą w D:\portal\apps\administracja\ (index.html, app.js, module.css)
-- i działa jar z endpointami /api/admin/** (paczka panelu). Bez restartu aplikacji.
--
-- Uprawnienia: WYŁĄCZNIE loginy imienne administratorów. Panel nigdy nie nada dostępu
-- do samego siebie — nowy administrator = kolejny INSERT poniżej, wykonany w SQL.
-- ======================================================================
USE [portal];
GO

INSERT INTO tiles (code, name, description, icon, tile_type, action_ref, active, display_order)
SELECT N'administracja',
       N'Administracja portalu',
       N'Kafelki, uprawnienia, wejścia i statystyki z rejestru audytu. Tylko odczyt; nadawanie uprawnień przez SQL (generator w panelu).',
       N'settings',
       'LINK',
       N'/apps/administracja/',
       1,
       900
WHERE NOT EXISTS (SELECT 1 FROM tiles WHERE code = N'administracja');
GO

INSERT INTO tile_permissions (tile_id, ad_group, permission_level)
SELECT t.id, N'maciej.mysliwiec', 'READ'
FROM tiles t
WHERE t.code = N'administracja'
  AND NOT EXISTS (SELECT 1 FROM tile_permissions p WHERE p.tile_id = t.id AND p.ad_group = N'maciej.mysliwiec');

INSERT INTO tile_permissions (tile_id, ad_group, permission_level)
SELECT t.id, N'zwonik.piotr', 'READ'
FROM tiles t
WHERE t.code = N'administracja'
  AND NOT EXISTS (SELECT 1 FROM tile_permissions p WHERE p.tile_id = t.id AND p.ad_group = N'zwonik.piotr');
GO

-- Kontrola: jeden kafelek, dwa uprawnienia.
SELECT t.id, t.code, t.active, t.display_order, p.ad_group, p.permission_level
FROM tiles t
LEFT JOIN tile_permissions p ON p.tile_id = t.id
WHERE t.code = N'administracja';
GO

-- Wyłączenie panelu (awaryjnie; wyłącza też /api/admin):  UPDATE tiles SET active = 0 WHERE code = N'administracja';
-- Włączenie z powrotem:                                     UPDATE tiles SET active = 1 WHERE code = N'administracja';
