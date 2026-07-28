# Runbook: profil `declared` (deklarowana tożsamość)

Decyzje i granice modelu: `docs/adr/0003-deklarowana-tozsamosc.md`. Tu wyłącznie
„jak uruchomić i jak używać".

## 1. Uruchomienie

**Laptop (dev):**

```
mvn -pl portal-api spring-boot:run -Dspring-boot.run.profiles=dev,declared
```

Flyway założy `user_departments` (V4 w głównej linii migracji). Bez wpisów
w tabeli każdy zadeklarowany login dostaje 403 — to poprawne (fail-closed);
najpierw sekcja 2. Dev-fallback z `application-dev.yml` w tym trybie **nie
działa** (filtr declared celowo go nie ma) — deklarację trzeba przysłać jawnie.

**Serwer jednostki:** profil `declared` + datasource jak w prod (env/yml lokalny).
**Nie łączyć z profilem `prod`** — prod aktywuje LDAP (wymagane env), a założeniem
trybu jest brak katalogu w runtime. Otwarcie na sieć = DWA przełączniki
w `application-declared.yml` (`server.address` + `allowed-cidrs`); opisy w pliku.
Ruch jest czystym HTTP — jeśli w jednostce działa IIS, można go postawić przed
portalem jako terminator TLS (bez Windows Auth; wtedy `allowed-cidrs` zostają
puste, bo żądania przychodzą z loopbacku, a realny adres klienta filtr i audyt
biorą z `X-Forwarded-For` — `ClientIpResolver` honoruje go wyłącznie zza loopbacku).

## 2. Prowizja danych (kto czym jest i co widzi)

**Przynależność** — `user_departments` (małe litery; CHECK pilnuje):

```sql
INSERT INTO user_departments (login, department) VALUES (N'jkowalski', N'finanse');
INSERT INTO user_departments (login, department) VALUES (N'anowak',    N'dag');
INSERT INTO user_departments (login, department) VALUES (N'anowak',    N'it');
```

Masowo: `deploy/declared/export-user-departments.ps1` (admin, ze swojej stacji)
generuje INSERT-y z AD — runtime katalogu nie dotyka.

**Uprawnienia kafelków** — istniejące `tile_permissions`; w kolumnie `ad_group`
zamiast grup AD stoją nazwy departamentów. Mapa w stylu YAML tłumaczy się
jeden do jednego (idiom INSERT-ów jak w seedach dev, bo klucz to `tile_id`):

| zamysł (YAML) | wiersze w `tile_permissions` (`ad_group`, `permission_level`) |
|---|---|
| `raporty-finansowe: [Finanse, Ksiegowosc]` | `'finanse','READ'` oraz `'ksiegowosc','READ'` |
| `panel-dag: [DAG, IT]` | `'dag','EXECUTE'` oraz `'it','EXECUTE'` |
| `informacje-ogolne: ["*"]` | `'wszyscy','READ'` |

```sql
INSERT INTO tile_permissions (tile_id, ad_group, permission_level)
SELECT id, 'wszyscy', 'READ' FROM tiles WHERE code = 'informacje-ogolne';

INSERT INTO tile_permissions (tile_id, ad_group, permission_level)
SELECT id, 'finanse', 'READ' FROM tiles WHERE code = 'raporty-finansowe';
```

Wildcard `*` = syntetyczna grupa `wszyscy`, którą serwer dokleja **tylko znanym
loginom** (obecnym w `user_departments`). Poziomy jak dotąd: `EXECUTE` pokrywa
`READ`. Zmiany działają bez restartu; propagacja do 10 min (cache grup).

## 3. Klient PowerShell

Skrypt woła **realne endpointy** i deklaruje wyłącznie login. Żadnych bramek
„verify", żadnego departamentu w żądaniu — serwer i tak by go zignorował:

```powershell
$headers = @{ 'X-Auth-User' = $env:USERNAME }
$base = 'http://portal-narzedzia.dzi.local:8080'

# co widzę? (lista przycięta przez serwer do uprawnień zadeklarowanego loginu)
$tiles = Invoke-RestMethod -Uri "$base/api/tiles" -Headers $headers

# uruchomienie kafelka SCRIPT — wykonuje SERWER (worker), skrypt tylko śledzi wynik
$task = Invoke-RestMethod -Uri "$base/api/tiles/etl-restart/run" -Method Post -Headers $headers
Invoke-RestMethod -Uri "$base/api/tasks/$($task.id)" -Headers $headers
```

Odmowa to `403` (wyjątek w `Invoke-RestMethod`) — decyzję podjął serwer i już
ją zaudytował; skrypt niczego nie „otwiera" po swojej stronie. `429` = limiter
(sufit żądań albo blokada po anomalii) — odczekać, nie pętlić.

## 4. Co ten tryb chroni, a czego nie

| Chroni przed | Nie chroni przed |
|---|---|
| „wpiszę departament i wejdę" — przynależności nie da się zadeklarować | podszyciem się pod **znany login** z zaufanej podsieci (brak sekretu — ADR-0003) |
| dostępem nieznanych loginów (fail-closed: 403, bez `wszyscy`) | odczytaniem ruchu w sieci (czysty HTTP, chyba że IIS/TLS z sekcji 1) |
| hałaśliwą enumeracją loginów (blokada adresu >3 loginów/10 min + 429 w audycie) | cierpliwym napastnikiem używającym jednego cudzego loginu (to wykrywa dopiero przegląd audytu: godziny, adres, wolumen) |
| zdalnym wstrzyknięciem nagłówka spoza CIDR (401) i CSRF z przeglądarki (SameOrigin + brak ambient credentials) | — |

Audyt mówi „**co zadeklarowano i skąd**", nie „kto siedział przy klawiaturze".
Przegląd `audit_log` pod kątem `DENIED`/`ERROR` i nietypowych godzin — okresowo,
jak w wariancie A. Danych wrażliwych za kafelki tego trybu nie wystawiamy.

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
