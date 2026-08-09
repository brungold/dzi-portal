-- =====================================================================
-- Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
-- Autor: Maciej Myśliwiec, 2026.
-- =====================================================================
-- V5: widok audytu z czasem polskim (odczyt operacyjny w sqlcmd).
-- Kolumna ts_utc ZOSTAJE w UTC (konwencja schematu z V1: "czas w UTC");
-- ts_pl to przeliczenie na strefe Polski z automatyczna obsluga
-- czasu letniego/zimowego (CEST/CET) przez AT TIME ZONE.
-- 'Central European Standard Time' to windowsowy identyfikator strefy
-- obejmujacy Warszawe WRAZ z regulami DST (SQL Server 2016+).
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
