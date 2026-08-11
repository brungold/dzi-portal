-- =====================================================================
-- Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
-- Autor: Maciej Myśliwiec, 2026.
-- =====================================================================
-- V4: kolejka zadań (uruchomienia skryptów przez workera PowerShell).
-- =====================================================================

CREATE TABLE tasks (
    id             BIGINT IDENTITY(1,1) NOT NULL,
    script_id      BIGINT        NOT NULL,
    tile_id        BIGINT        NULL,
    requested_by   NVARCHAR(100) NOT NULL,
    params         NVARCHAR(MAX) NULL,
    status         VARCHAR(20)   NOT NULL CONSTRAINT df_tasks_status DEFAULT 'PENDING',
    correlation_id CHAR(36)      NOT NULL,     -- spina wpisy audytu API z przebiegiem w workerze
    created_at     DATETIME2     NOT NULL,
    started_at     DATETIME2     NULL,
    finished_at    DATETIME2     NULL,
    exit_code      INT           NULL,
    worker_host    NVARCHAR(100) NULL,
    version        INT           NOT NULL CONSTRAINT df_tasks_version DEFAULT 0,  -- optimistic lock (Spring Data JDBC @Version)
    CONSTRAINT pk_tasks PRIMARY KEY (id),
    CONSTRAINT fk_tasks_script FOREIGN KEY (script_id) REFERENCES scripts (id),
    CONSTRAINT fk_tasks_tile FOREIGN KEY (tile_id) REFERENCES tiles (id),
    CONSTRAINT ck_tasks_status CHECK (status IN ('PENDING','IN_PROGRESS','SUCCEEDED','FAILED','TIMED_OUT','CANCELLED'))
);

-- Pod polling workera: szukamy najstarszych PENDING
CREATE INDEX ix_tasks_status_created ON tasks (status, created_at);
