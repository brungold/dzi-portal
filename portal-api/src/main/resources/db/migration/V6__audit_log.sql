-- =====================================================================
-- Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
-- Autor: Maciej Myśliwiec, 2026.
-- =====================================================================
-- V6: audyt żądań HTTP i zdarzeń biznesowych. Postać docelowa — kolumna
-- object_ref i indeks po action naniesione z dawnej migracji V2 (Etap 2).
-- ts_utc: aplikacja zapisuje jawnie ściankę zegara UTC (AuditWriter,
-- poprawka D3 z 2026-08-06). Odczyt w czasie polskim: widok v_audit_log_pl.
-- Append-only egzekwują uprawnienia SQL w prod: deploy/sql/prod-grants.sql
-- (DENY UPDATE/DELETE dla kont runtime) — tu celowo brak GRANT-ów.
-- =====================================================================

CREATE TABLE audit_log (
    id             BIGINT IDENTITY(1,1) NOT NULL,
    ts_utc         DATETIME2     NOT NULL,
    username       NVARCHAR(100) NOT NULL,   -- '-' gdy żądanie bez tożsamości
    client_ip      VARCHAR(45)   NOT NULL,   -- z X-Forwarded-For, honorowany tylko od IIS (loopback)
    http_method    VARCHAR(10)   NOT NULL,
    path           NVARCHAR(400) NOT NULL,   -- URI bez query stringa (parametry celowo poza audytem HTTP)
    action         NVARCHAR(100) NULL,       -- zdarzenia biznesowe (@Audited)
    object_ref     NVARCHAR(200) NULL,       -- obiekt akcji, np. 'tile:42', 'task:1007'
    status         VARCHAR(10)   NOT NULL,
    http_status    SMALLINT      NOT NULL,
    duration_ms    INT           NOT NULL,
    correlation_id CHAR(36)      NOT NULL,
    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    CONSTRAINT ck_audit_log_status CHECK (status IN ('SUCCESS','DENIED','ERROR'))
);

CREATE INDEX ix_audit_log_ts ON audit_log (ts_utc);
CREATE INDEX ix_audit_log_user ON audit_log (username, ts_utc);
-- Pod przegląd audytu: "pokaż wszystkie TILE_EXECUTE z ostatniego tygodnia"
CREATE INDEX ix_audit_log_action ON audit_log (action, ts_utc);
