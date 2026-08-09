-- =====================================================================
-- Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
-- Autor: Maciej Myśliwiec, 2026.
-- =====================================================================
-- V6: widok task_log z czasem polskim — symetrycznie do v_audit_log_pl (V5),
-- zeby odczyt operacyjny obu rejestrow wygladal tak samo.
-- task_log zawsze zapisywal prawdziwy UTC (DEFAULT SYSUTCDATETIME z V1),
-- wiec zadna korekta danych nie jest tu potrzebna.
-- =====================================================================
CREATE VIEW dbo.v_task_log_pl AS
SELECT id,
       CAST((ts_utc AT TIME ZONE 'UTC') AT TIME ZONE 'Central European Standard Time'
            AS DATETIME2(3)) AS ts_pl,
       ts_utc,
       task_id,
       stream,
       line
FROM dbo.task_log;
