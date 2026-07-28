# Etap 3 — paczka: kafelki + dwustronny RBAC + portal-bootstrap.js

Pierwszy moment, w którym portal "wygląda": przeglądarka pokazuje kafelki
przefiltrowane po grupach AD, a ręcznie sklejony request na cudzy kafelek
kończy się 403 z wpisem DENIED w audycie.

## Dwustronne egzekwowanie — sedno etapu

| Poziom | Mechanizm | Co daje |
|---|---|---|
| miękki (UX) | `GET /api/tiles` zwraca tylko kafelki z uprawnieniem ≥ READ; bootstrap ukrywa resztę | użytkownik nie widzi, czego nie może |
| twardy | `@PreAuthorize("@access.canRead(#code, authentication)")` → `AccessFacade` | znajomość kodu kafelka nic nie daje: 403 + DENIED w audycie (action z @Audited już ustawione) |

Zasady w `AccessFacade`: hierarchia liniowa READ < EXECUTE < EDIT (jedno nadanie
EXECUTE wystarcza do widoczności), grupy porównywane case-insensitive jak w AD,
nieznany/nieaktywny kod ⇒ false ⇒ 403 (brak uprawnień nie zdradza istnienia zasobu),
pusty zbiór grup ⇒ odmowa przed dotknięciem bazy (przy okazji: SQL `IN ()` nie istnieje).

## Pliki

**Zmienione (5):** `application-dev.yml` (flyway dev-seed + statyka z `../frontend`) ·
`DevSecurityConfiguration.java` (łańcuch Order(0) dla statyki w dev) ·
`SecurityConfig.java` i `AdGroupResolver.java` (public — testy plasterkowe modułów
biznesowych importują prawdziwy łańcuch security) · `frontend/README.md` (kontrakt integracji)

**Nowe (16):** moduł `tiles/` (9 klas: encje, `PermissionLevel`, lean repozytoria na bazie
`Repository` — in-memory double ma 20 linii zamiast całego CRUD, fasady, kontroler,
konfiguracja) · `db/migration-dev/V100__dev_seed_tiles.sql` · `frontend/portal-bootstrap.js` ·
`frontend/index.example.html` · 4 pliki testów

## Plan commitów (kontynuacja)

| # | Commit | Zakres |
|---|--------|--------|
| 13 | `feat(db): dev-seed kafelków w osobnej lokalizacji Flyway` | `V100__dev_seed_tiles.sql`, `application-dev.yml` |
| 14 | `feat(security): SecurityConfig i AdGroupResolver publiczne dla testów modułów` | 2 pliki security |
| 15 | `feat(tiles): model, fasady i REST z dwustronnym RBAC` | `tiles/**`, testy |
| 16 | `feat(frontend): portal-bootstrap.js + statyka w dev` | `frontend/**`, `DevSecurityConfiguration` |

## Jak to zobaczyć (2 minuty)

1. `mvn -pl portal-api -am clean verify`, potem start z profilem dev (Flyway dołoży V100).
2. Przeglądarka: `http://localhost:8080/index.example.html` — dev-fallback loguje jako
   `tester` (Admin): 4 kafelki, Restart ETL klikalny, w rogu pasek `tester · kafelki: 4`,
   piąty kafelek z przykładu ukryty (patrz console.info).
3. Zmień w `application-dev.yml` `dev-fallback-user: viewer`, restart, odśwież:
   znika Panel administratora, Restart ETL przygaszony (canExecute=false).
4. Twardy RBAC z konsoli:
   `curl -i -H "X-Auth-User: DZI\viewer" http://localhost:8080/api/tiles/admin-tylko` → **403**,
   a w `audit_log` wpis DENIED z `action = 'TILE_READ'`.

## Integracja z plikiem kolegi

Dokładnie dwie zmiany w jego index.html: `data-tile-id` na kafelkach + jeden `<script>`.
Checklist przeglądu (CDN-y, atrapy onclick) — `frontend/README.md`. Plik podmienia się
w `frontend/` i działa bez rekompilacji czegokolwiek.

## Zarządzanie kafelkami w prod

Na razie INSERT-y wykonuje administrator według wzoru z V100 (seed dev). Panel CRUD
admina — backlog; świadomie poza zakresem, dopóki kafelków jest kilkanaście.

## Zastrzeżenia

1. Kolejność na liście = `display_order, name` (robi ją SQL; double w testach odwzorowuje).
2. Odmowa z @PreAuthorize pada przed ciałem metody, więc `object_ref` w DENIED jest pusty —
   ale `action` i path z kodem kafelka są; to wystarcza audytorowi i tak jest taniej.
3. `spring.web.resources.static-locations` z `file:../frontend/` zakłada uruchamianie przez
   `mvn -pl portal-api spring-boot:run` (working dir = katalog modułu). Przy uruchamianiu
   jara z innego katalogu popraw ścieżkę albo pomiń — to wygoda dev, prod tego nie używa.

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
