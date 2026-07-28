-- =====================================================================
-- V1: fundament portalu (Etap 0)
-- Zakres: kafelki + uprawnienia, whitelist skryptów, kolejka zadań, audyt.
-- Tabele pod dane raportowe (datasets) wejdą jako V2 przy Etapie 5.
-- Konwencje: nazwy angielskie snake_case, czas w UTC (DATETIME2).
-- =====================================================================

CREATE TABLE tiles (
    id            BIGINT IDENTITY(1,1) NOT NULL,
    code          NVARCHAR(50)   NOT NULL,  -- stały identyfikator; frontend: data-tile-id
    name          NVARCHAR(200)  NOT NULL,
    description   NVARCHAR(1000) NULL,
    icon          NVARCHAR(50)   NULL,
    tile_type     VARCHAR(20)    NOT NULL,
    action_ref    NVARCHAR(400)  NULL,      -- SCRIPT: scripts.code | REPORT: kod zbioru (V2) | LINK: URL
    active        BIT            NOT NULL CONSTRAINT df_tiles_active DEFAULT 1,
    display_order INT            NOT NULL CONSTRAINT df_tiles_order DEFAULT 100,
    CONSTRAINT pk_tiles PRIMARY KEY (id),
    CONSTRAINT uq_tiles_code UNIQUE (code),
    CONSTRAINT ck_tiles_type CHECK (tile_type IN ('SCRIPT','REPORT','LINK'))
);

CREATE TABLE tile_permissions (
    id               BIGINT IDENTITY(1,1) NOT NULL,
    tile_id          BIGINT        NOT NULL,
    ad_group         NVARCHAR(200) NOT NULL,  -- sAMAccountName grupy AD (prefiks DZI-Portal-)
    permission_level VARCHAR(20)   NOT NULL,
    CONSTRAINT pk_tile_permissions PRIMARY KEY (id),
    CONSTRAINT fk_tile_permissions_tile FOREIGN KEY (tile_id) REFERENCES tiles (id),
    CONSTRAINT uq_tile_permissions UNIQUE (tile_id, ad_group, permission_level),
    CONSTRAINT ck_tile_permissions_level CHECK (permission_level IN ('READ','EXECUTE','EDIT'))
);

CREATE TABLE scripts (
    id              BIGINT IDENTITY(1,1) NOT NULL,
    code            NVARCHAR(50)  NOT NULL,
    path            NVARCHAR(500) NOT NULL,   -- wyłącznie z katalogu skryptów; nigdy z żądania
    script_type     VARCHAR(10)   NOT NULL,
    params_schema   NVARCHAR(MAX) NULL,       -- JSON Schema parametrów (walidacja w API, Etap 4)
    timeout_seconds INT           NOT NULL CONSTRAINT df_scripts_timeout DEFAULT 300,
    active          BIT           NOT NULL CONSTRAINT df_scripts_active DEFAULT 1,
    CONSTRAINT pk_scripts PRIMARY KEY (id),
    CONSTRAINT uq_scripts_code UNIQUE (code),
    CONSTRAINT ck_scripts_type CHECK (script_type IN ('PS1','BAT','EXE','JAR'))
);

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

CREATE TABLE audit_log (
    id             BIGINT IDENTITY(1,1) NOT NULL,
    ts_utc         DATETIME2     NOT NULL,
    username       NVARCHAR(100) NOT NULL,   -- '-' gdy żądanie bez tożsamości
    client_ip      VARCHAR(45)   NOT NULL,   -- z X-Forwarded-For, honorowany tylko od IIS (loopback)
    http_method    VARCHAR(10)   NOT NULL,
    path           NVARCHAR(400) NOT NULL,   -- URI bez query stringa (parametry celowo poza audytem HTTP)
    action         NVARCHAR(100) NULL,       -- zdarzenia biznesowe (@Audited) — od Etapu 3+
    status         VARCHAR(10)   NOT NULL,
    http_status    SMALLINT      NOT NULL,
    duration_ms    INT           NOT NULL,
    correlation_id CHAR(36)      NOT NULL,
    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    CONSTRAINT ck_audit_log_status CHECK (status IN ('SUCCESS','DENIED','ERROR'))
);

CREATE INDEX ix_audit_log_ts ON audit_log (ts_utc);
CREATE INDEX ix_audit_log_user ON audit_log (username, ts_utc);

-- Append-only dla audit_log egzekwują uprawnienia SQL w prod: deploy/sql/prod-grants.sql
-- (DENY UPDATE/DELETE dla kont gMSA). Tu celowo brak GRANT-ów — loginy runtime nie istnieją w dev.
