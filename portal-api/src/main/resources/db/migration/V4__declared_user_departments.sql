-- V4: przynależność login -> departament dla profilu declared (ADR-0003).
-- W wariancie A (prod z Kerberosem/LDAPS) tabela istnieje, ale pozostaje pusta
-- i nieużywana — jedna linia migracji, jeden właściciel schematu (ADR-0001, dec. 4).
--
-- Konwencja danych: loginy (sAMAccountName) i departamenty MAŁYMI literami —
-- egzekwują ją CHECK-i poniżej oraz skrypt prowizji deploy/declared/*.ps1.
-- To dane referencyjne zarządzane jak tile_permissions (INSERT/DELETE przez
-- administratora w SQL); NIE podlegają reżimowi append-only jak audit_log.

CREATE TABLE user_departments (
    id         BIGINT IDENTITY(1,1) NOT NULL,
    login      NVARCHAR(128) NOT NULL,
    department NVARCHAR(100) NOT NULL,
    CONSTRAINT pk_user_departments PRIMARY KEY (id),
    CONSTRAINT uq_user_departments UNIQUE (login, department),
    CONSTRAINT ck_user_departments_login_lower      CHECK (login = LOWER(login)),
    CONSTRAINT ck_user_departments_department_lower CHECK (department = LOWER(department))
);

CREATE INDEX ix_user_departments_login ON user_departments (login);
