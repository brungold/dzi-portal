# Runbook: profil `declared` (deklarowana tożsamość — login + departament)

Decyzje i granice modelu: `docs/adr/0003-deklarowana-tozsamosc.md` (zaufanie,
limiter, audyt) + `docs/adr/0005-deklarowany-departament.md` (przynależność).
Tu wyłącznie „jak uruchomić i jak używać".

## 1. Uruchomienie

**Laptop (dev):**

```
mvn -pl portal-api spring-boot:run -Dspring-boot.run.profiles=dev,declared
```

Dev-fallback z `application-dev.yml` w tym trybie **nie działa** (filtr declared
celowo go nie ma) — deklarację (login + departament) trzeba przysłać jawnie
nagłówkami `X-Auth-User` i `X-Auth-Dept`.

**Serwer jednostki:** profil `declared` + datasource w lokalnym
`config/application-declared.yml`. **Nie łączyć z profilem `prod`** — prod
aktywuje LDAP, a założeniem trybu jest brak katalogu w runtime. Otwarcie na sieć
= DWA przełączniki w `application-declared.yml` (`server.address` + `allowed-cidrs`).
Ruch jest czystym HTTP — IIS przed portalem jako terminator TLS (bez Windows Auth;
wtedy `allowed-cidrs` zostają puste, bo żądania przychodzą z loopbacku, a realny
adres klienta filtr i audyt biorą z `X-Forwarded-For`, honorowanego wyłącznie
zza loopbacku; reguła w IIS ma NADPISYWAĆ ten nagłówek).

## 2. Uprawnienia kafelków (jedyne miejsce zarządzania dostępem)

Zbiór uprawnień żądania to **{login, departament, wszyscy}** — w kolumnie
`tile_permissions.ad_group` wolno więc mieszać skróty departamentów
(z AD `extensionattribute12`: dzi/dag/dpb…, małymi literami) i loginy:

```sql
-- cały departament
INSERT INTO tile_permissions (tile_id, ad_group, permission_level)
SELECT id, 'dzi', 'READ' FROM tiles WHERE code = 'raport-systemow';

-- kilka departamentów + osoby imiennie (jeden kafelek, wiele wierszy)
INSERT INTO tile_permissions (tile_id, ad_group, permission_level)
SELECT id, v.g, 'READ' FROM tiles
CROSS APPLY (VALUES ('dag'),('dpb'),('maciej.mysliwiec'),('zwonik.piotr')) v(g)
WHERE code = 'wspolny-raport';

-- tylko imiennie (np. panel administracyjny)
INSERT INTO tile_permissions (tile_id, ad_group, permission_level)
SELECT id, 'maciej.mysliwiec', 'EDIT' FROM tiles WHERE code = 'panel-admin';

-- kafelek publiczny
INSERT INTO tile_permissions (tile_id, ad_group, permission_level)
SELECT id, 'wszyscy', 'READ' FROM tiles WHERE code = 'informacje-ogolne';
```

Zasady: poziomy jak dotąd (`EDIT` ⊃ `EXECUTE` ⊃ `READ`); zmiany działają bez
restartu; **`wszyscy` = dosłownie każdy, kto dotrze do portalu** (ADR-0005);
loginy sprawdzaj w AD przed wpisaniem — formaty bywają różne
(imie.nazwisko / nazwisko.imie):

```powershell
([adsisearcher]"(sn=Kowalski)").FindAll() | ForEach-Object { $_.Properties['samaccountname'][0] }
```

Uprawnienia imienne trzymaj na wyjątki — regułą jest departament.

## 3. Klienci

**PowerShell** — `deploy/declared/portal-client.ps1`: login i departament czyta
z AD dla zalogowanego użytkownika (nic się nie wpisuje), dokłada oba nagłówki,
listuje kafelki, opcjonalnie uruchamia SCRIPT i śledzi wynik. Ręcznie:

```powershell
$p = ([adsisearcher]"(samaccountname=$env:USERNAME)").FindOne().Properties
$headers = @{
    'X-Auth-User' = $p['samaccountname'][0].ToString().ToLower()
    'X-Auth-Dept' = $p['extensionattribute12'][0].ToString().ToLower()
}
Invoke-RestMethod -Uri 'https://portal.dzi.pl/api/tiles' -Headers $headers
```

**Przeglądarka** — `frontend/js/declared-identity.js`: sonda `/api/whoami`,
przy 401 okno z polami login + departament (zapamiętywane w localStorage;
zmiana: klik w login w nagłówku strony albo `PortalIdentity.change()` z konsoli).

Odmowa = `403` (decyzję podjął serwer i zaudytował). `429` = limiter — odczekać.

## 4. Co ten tryb chroni, a czego nie

| Chroni przed | Nie chroni przed |
|---|---|
| deklaracją spoza zaufanych źródeł (401: tylko loopback/CIDR) | wpisaniem CUDZEGO departamentu lub loginu — deklaracja nie jest dowodzona (ADR-0005) |
| hałaśliwą enumeracją (blokada adresu >3 loginy/10 min, sufit 120/min, 429 w audycie) | cierpliwym użyciem jednej cudzej tożsamości (wykrywa dopiero przegląd audytu: godziny, adres, wolumen) |
| CSRF z przeglądarki (SameOrigin, brak ambient credentials) | odczytem ruchu w sieci (czysty HTTP — stąd IIS/TLS z §1) |
| pominięciem serwera (każdy zasób /api egzekwowany, DENIED w audycie) | — |

Audyt mówi „**co zadeklarowano i skąd**" (login, departament, adres), nie „kto
siedział przy klawiaturze". Przegląd `audit_log` pod kątem `DENIED`/`ERROR`
i nietypowych godzin — okresowo. **Danych wrażliwych za kafelkami tego trybu
nie wystawiamy** — to warunek przyjęcia modelu, nie sugestia.

---

*Autor: Maciej Myśliwiec, 2026.*
