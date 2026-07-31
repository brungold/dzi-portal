-- ======================================================================
-- Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
-- Autor: Maciej Myśliwiec, 2026.
-- ======================================================================
-- ======================================================================
-- Retencja rejestrow (Etap 6). URUCHAMIA konto UTRZYMANIOWE, NIGDY gMSA
-- aplikacji - DENY UPDATE/DELETE na audit_log dla kont runtime ma zostac
-- nienaruszone (to jest cala wartosc append-only).
-- Konto wykonujace potrzebuje jednorazowo:
--   GRANT DELETE ON dbo.audit_log TO [DZI\konto-utrzymaniowe];
--   GRANT DELETE ON dbo.task_log  TO [DZI\konto-utrzymaniowe];
-- Wywolanie (sqlcmd, zmienne SQLCMD):
--   sqlcmd -E -S localhost -d portal -i audit-retention.sql -b
-- ======================================================================
:setvar AuditRetentionDays 400
:setvar TaskLogRetentionDays 90

SET NOCOUNT ON;
DECLARE @cutoffAudit DATETIME2 = DATEADD(DAY, -$(AuditRetentionDays), SYSUTCDATETIME());
DECLARE @cutoffTaskLog DATETIME2 = DATEADD(DAY, -$(TaskLogRetentionDays), SYSUTCDATETIME());
DECLARE @deleted INT = 1;

-- Partiami po 5000: krotkie transakcje, log transakcyjny Express nie puchnie.
WHILE @deleted > 0
BEGIN
    DELETE TOP (5000) FROM dbo.audit_log WHERE ts_utc < @cutoffAudit;
    SET @deleted = @@ROWCOUNT;
END;

SET @deleted = 1;
WHILE @deleted > 0
BEGIN
    DELETE TOP (5000) FROM dbo.task_log WHERE ts_utc < @cutoffTaskLog;
    SET @deleted = @@ROWCOUNT;
END;

PRINT CONCAT('Retencja OK: audit_log < ', CONVERT(VARCHAR(19), @cutoffAudit, 126),
             ', task_log < ', CONVERT(VARCHAR(19), @cutoffTaskLog, 126));
-- Wiersze tasks celowo ZOSTAJA (rejestr biznesowy zlecen); czyscimy tylko ich stdout/stderr.
