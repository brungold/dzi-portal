-- =====================================================================
-- Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
-- Autor: Maciej Myśliwiec, 2026.
-- =====================================================================
-- V12: widok audytu z czasem polskim (odczyt operacyjny w sqlcmd).
-- Kolumna ts_utc ZOSTAJE w UTC (konwencja schematu); ts_pl to przeliczenie
-- na strefę Polski z automatyczną obsługą czasu letniego/zimowego.
-- 'Central European Standard Time' to windowsowy identyfikator strefy
-- obejmujący Warszawę WRAZ z regułami DST (SQL Server 2016+).
-- Odczyt: SELECT TOP 20 * FROM v_audit_log_pl ORDER BY id DESC;
-- =====================================================================
CREATE VIEW dbo.v_audit_log_pl AS
SELECT id,
       CAST((ts_utc AT TIME ZONE 'UTC') AT TIME ZONE 'Central European Standard Time'
            AS DATETIME2(3)) AS ts_pl,
       ts_utc,
       username,
       client_ip,
       http_method,
       path,
       action,
       object_ref,
       status,
       http_status,
       duration_ms,
       correlation_id
FROM dbo.audit_log;
