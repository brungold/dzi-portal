-- =====================================================================
-- V2 (Etap 2): audyt zdarzeń biznesowych.
-- action (kolumna z V1) zaczyna być wypełniana przez @Audited na endpointach;
-- object_ref wskazuje obiekt, którego akcja dotyczy (np. 'tile:42', 'task:1007').
-- Migracje są niemutowalne — komentarz w V1 "action od Etapu 3+" pozostaje
-- historyczny; stan faktyczny opisuje ta migracja.
-- =====================================================================

ALTER TABLE audit_log ADD object_ref NVARCHAR(200) NULL;

-- Pod przyszły przegląd audytu: "pokaż wszystkie TILE_EXECUTE z ostatniego tygodnia"
CREATE INDEX ix_audit_log_action ON audit_log (action, ts_utc);
