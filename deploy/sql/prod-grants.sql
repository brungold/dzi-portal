-- ======================================================================
-- Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
-- Autor: Maciej Myśliwiec, 2026.
-- ======================================================================
-- PROD, profil `declared` (ADR-0003/0006). Stan kont zweryfikowany 2026-09-08.
--
-- Zasada: konta runtime NIE są db_owner, więc DENY jest skuteczny
-- (DENY wygrywa z GRANT-em z ról). audit_log jest append-only na poziomie
-- uprawnień, nie na poziomie obietnicy.
--
-- Konta w bazie [portal] (uwierzytelnianie SQL, mixed mode):
--   portal_app        — konto aplikacji: db_ddladmin (Flyway) + db_datareader + db_datawriter
--   maciej.mysliwiec  — konto robocze do SSMS/sqlcmd: db_datareader + db_datawriter
--   zwonik.piotr      — konto robocze do SSMS/sqlcmd: db_datareader + db_datawriter
-- Właściciel bazy (dbo) = konto Windows administratora instancji — POZA zasięgiem
-- DENY jako sysadmin; to jest jedyna ścieżka do retencji (audit-retention.sql).
--
-- Historia: wariant A (gMSA-PortalApi / gMSA-PortalWorker, integrated security)
-- — patrz ten plik w commitach sprzed 2026-09; profil `prod` nie jest używany.
-- ======================================================================
USE [portal];
GO

-- Append-only dla audytu: konta mogą wstawiać (aplikacja = jeden INSERT),
-- nie mogą poprawiać ani kasować istniejących wpisów.
DENY UPDATE, DELETE ON dbo.audit_log TO [portal_app];
DENY UPDATE, DELETE ON dbo.audit_log TO [maciej.mysliwiec];
DENY UPDATE, DELETE ON dbo.audit_log TO [zwonik.piotr];
GO

-- Weryfikacja 1: trzy konta x dwa wiersze DENY.
SELECT USER_NAME(grantee_principal_id) AS konto, permission_name, state_desc
FROM sys.database_permissions
WHERE major_id = OBJECT_ID('dbo.audit_log')
ORDER BY konto, permission_name;
GO

-- Weryfikacja 2: próba UPDATE jako konto aplikacji (impersonacja przez
-- administratora instancji, bez hasła). Oczekiwane: 'OK: The UPDATE permission
-- was denied ...'. Wiersz z id = -1 nie istnieje — sprawdzane jest uprawnienie,
-- nie dane.
EXECUTE AS USER = 'portal_app';
BEGIN TRY
    UPDATE dbo.audit_log SET status = status WHERE id = -1;
    PRINT 'BLAD: UPDATE jako portal_app PRZESZEDL';
END TRY
BEGIN CATCH
    PRINT 'OK: ' + ERROR_MESSAGE();
END CATCH;
REVERT;
GO

-- Odwrócenie (gdyby było potrzebne):
--   REVOKE UPDATE, DELETE ON dbo.audit_log FROM [portal_app];
--   REVOKE UPDATE, DELETE ON dbo.audit_log FROM [maciej.mysliwiec];
--   REVOKE UPDATE, DELETE ON dbo.audit_log FROM [zwonik.piotr];
--
-- Backlog (ADR-0001 dec. 7 / ADR-0002 dec. 4): rozdzielić tożsamość migracyjną
-- od runtime i zdjąć db_ddladmin z portal_app — próg: pipeline wdrożeniowy.
