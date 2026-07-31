-- ======================================================================
-- Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
-- Autor: Maciej Myśliwiec, 2026.
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
