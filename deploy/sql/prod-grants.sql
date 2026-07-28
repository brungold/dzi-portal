-- ======================================================================
-- Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
-- Autor: Maciej Myśliwiec, 2026.
-- Autorskie prawa osobiste (prawo do autorstwa) niezbywalne — art. 16 pr. aut.
-- Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
-- ======================================================================
-- ======================================================================
-- PROD. Uruchamia administrator bazy po utworzeniu bazy [portal].
-- Zasada: konta runtime NIE sa db_owner; audit_log jest append-only
-- na poziomie uprawnien (DENY wygrywa z GRANT-em z rol).
-- ======================================================================
USE [master];
GO
CREATE LOGIN [DZI\gMSA-PortalApi$]    FROM WINDOWS;
CREATE LOGIN [DZI\gMSA-PortalWorker$] FROM WINDOWS;
GO
USE [portal];
GO
CREATE USER [DZI\gMSA-PortalApi$]    FOR LOGIN [DZI\gMSA-PortalApi$];
CREATE USER [DZI\gMSA-PortalWorker$] FOR LOGIN [DZI\gMSA-PortalWorker$];
GO
ALTER ROLE db_datareader ADD MEMBER [DZI\gMSA-PortalApi$];
ALTER ROLE db_datawriter ADD MEMBER [DZI\gMSA-PortalApi$];
-- api uruchamia migracje Flyway przy starcie -> potrzebuje DDL:
ALTER ROLE db_ddladmin   ADD MEMBER [DZI\gMSA-PortalApi$];

ALTER ROLE db_datareader ADD MEMBER [DZI\gMSA-PortalWorker$];
ALTER ROLE db_datawriter ADD MEMBER [DZI\gMSA-PortalWorker$];
GO
-- Append-only dla audytu (konta nie sa db_owner, wiec DENY jest skuteczny):
DENY UPDATE, DELETE ON dbo.audit_log TO [DZI\gMSA-PortalApi$];
DENY UPDATE, DELETE ON dbo.audit_log TO [DZI\gMSA-PortalWorker$];
GO
-- Hardening (Etap 6, ADR-0001): rozdzielic tozsamosc migracyjna od runtime
-- (osobny login z db_ddladmin uzywany wylacznie w oknie wdrozenia),
-- wtedy zdjac db_ddladmin z gMSA-PortalApi.
