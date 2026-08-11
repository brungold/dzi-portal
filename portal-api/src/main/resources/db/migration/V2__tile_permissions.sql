-- =====================================================================
-- Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
-- Autor: Maciej Myśliwiec, 2026.
-- =====================================================================
-- V2: uprawnienia kafelków. ad_group przyjmuje: 'wszyscy' (grupa syntetyczna,
-- każde żądanie), login (sAMAccountName małymi literami, np. jan.kowalski)
-- lub — docelowo — nazwę grupy/departamentu. Egzekwowanie: AccessFacade
-- (miękko: lista kafelków; twardo: 403 + wpis DENIED w audycie).
-- =====================================================================

CREATE TABLE tile_permissions (
    id               BIGINT IDENTITY(1,1) NOT NULL,
    tile_id          BIGINT        NOT NULL,
    ad_group         NVARCHAR(200) NOT NULL,
    permission_level VARCHAR(20)   NOT NULL,
    CONSTRAINT pk_tile_permissions PRIMARY KEY (id),
    CONSTRAINT fk_tile_permissions_tile FOREIGN KEY (tile_id) REFERENCES tiles (id),
    CONSTRAINT uq_tile_permissions UNIQUE (tile_id, ad_group, permission_level),
    CONSTRAINT ck_tile_permissions_level CHECK (permission_level IN ('READ','EXECUTE','EDIT'))
);
