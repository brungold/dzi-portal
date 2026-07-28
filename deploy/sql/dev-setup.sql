-- ======================================================================
-- Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
-- Autor: Maciej Myśliwiec, 2026.
-- Autorskie prawa osobiste (prawo do autorstwa) niezbywalne — art. 16 pr. aut.
-- Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
-- ======================================================================
-- ======================================================================
-- DEV ONLY. Uruchom jako administrator instancji (SSMS / sqlcmd).
-- Tworzy baze i login uzywane przez profil 'dev' obu aplikacji.
-- ======================================================================
CREATE DATABASE portal_dev;
GO
CREATE LOGIN portal_dev WITH PASSWORD = 'PortalDev123!', CHECK_POLICY = OFF;
GO
USE portal_dev;
GO
CREATE USER portal_dev FOR LOGIN portal_dev;
ALTER ROLE db_owner ADD MEMBER portal_dev;  -- dev: pelne prawa, Flyway tworzy schemat przy starcie api
GO
