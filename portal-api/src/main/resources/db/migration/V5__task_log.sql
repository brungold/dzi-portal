-- =====================================================================
-- Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
-- Autor: Maciej Myśliwiec, 2026.
-- =====================================================================
-- V5: rejestr wykonania zadań (stdout/stderr/system, linia po linii).
-- ts_utc z DEFAULT SYSUTCDATETIME() — zawsze prawdziwy UTC.
-- =====================================================================

CREATE TABLE task_log (
    id      BIGINT IDENTITY(1,1) NOT NULL,
    task_id BIGINT         NOT NULL,
    ts_utc  DATETIME2      NOT NULL CONSTRAINT df_task_log_ts DEFAULT SYSUTCDATETIME(),
    stream  VARCHAR(10)    NOT NULL,
    line    NVARCHAR(4000) NOT NULL,
    CONSTRAINT pk_task_log PRIMARY KEY (id),
    CONSTRAINT fk_task_log_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT ck_task_log_stream CHECK (stream IN ('STDOUT','STDERR','SYSTEM'))
);

CREATE INDEX ix_task_log_task ON task_log (task_id, id);
