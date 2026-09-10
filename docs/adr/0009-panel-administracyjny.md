# ADR-0009: Panel administracyjny jako kafelek (Faza A — odczyt)

Status: zaakceptowany · Data: 2026-09-09 · Rewiduje §7 briefu („bez panelu — celowo") · Dotyczy: portal-api, apps/administracja

## Kontekst

Dostępy nadawane są w SQL (`tile_permissions`), a drugi administrator nie czuje się
w tym pewnie. Cztery kafelki, ~40 wpisów uprawnień, loginy w organizacji o mieszanym
formacie (nazwisko.imię / imię.nazwisko / sufiksy cyfrowe) — literówka w loginie daje
kafelek niewidoczny **bez żadnego błędu** (pułapka nr 1 tego projektu). Pytania
„kto widzi kafelek", „co widzi osoba", „kto wchodził" wymagają dziś SSMS i znajomości
`v_audit_log_pl`. Próg z briefu (>15–20 kafelków) nie został przekroczony, ale to nie
liczba kafelków, tylko liczba **osób** administrujących i koszt pomyłki uzasadnia panel.

## Decyzja

**1. Panel jest zwykłym kafelkiem** (`administracja`, LINK → `/apps/administracja/`),
chronionym tym samym mechanizmem co każdy moduł: READ w `tile_permissions` egzekwowany
przez strażnika dla plików (ADR-0007) i przez `@PreAuthorize("@access.canRead('administracja', …)")`
na każdym endpoincie `/api/admin/**`. Żadnej nowej roli, tabeli ani migracji.

**2. Faza A = wyłącznie odczyt.** Sześć endpointów GET (kafelki, kafelek → reguły + odwiedzający,
osoba → kafelki + wejścia, loginy znane portalowi, statystyka w zakresie dat, ostatnie wpisy
audytu). Zero endpointów zapisu, więc: bez CSRF, bez reguł nietykalności, bez walidacji
wsadów. Nadawanie uprawnień nadal w SQL — panel generuje gotowe `INSERT` z loginem
sprawdzonym w audycie (sekcja „Generator SQL").

**3. Definicja „wejścia" = wpis audytu `APP_OPEN` z `object_ref = 'tile:<kod>'`**
(otwarcie strony modułu). Pobrania danych (`APP_DATA`), pliki css/js i odświeżenia z 304
nie są wejściami. „Wejście na portal" = `TILES_LIST`. Statystyki liczone z `audit_log`
po istniejących indeksach; żadnego osobnego licznika.

**4. Panel nie zna departamentu osoby** (ADR-0005/0008: departament przychodzi z nagłówka
per żądanie) i tego nie udaje. Widok „co widzi osoba" pokazuje kafelki imienne i „wszyscy"
jako pewne, a departamentowe jako warunkowe („jeśli w: dzi").

**5. Login nieznany portalowi = ostrzeżenie, nie blokada.** Wpis w `tile_permissions`,
którego login nigdy nie pojawił się w `audit_log.username`, dostaje ⚠. To wykrywacz
literówek; nie odróżnia literówki od osoby, która jeszcze nie weszła — i mówi to wprost.

**6. Nowy administrator = `INSERT` w SQL.** Panel nie ma i nie będzie miał endpointu
nadającego dostęp do samego siebie. `active = 0` na kafelku wyłącza panel razem z API —
powrót przez SQL (`deploy/sql/apps-administracja.sql`).

**7. Kod panelu jest w repo** (`apps/administracja/` — wyjątek w `.gitignore`), inaczej
niż moduły treściowe. Powód: front jest sprzężony z wersją jara (`/api/admin/**`) —
muszą być wersjonowane razem.

## Konsekwencje

- Każde wywołanie `/api/admin/**` trafia do audytu jako `ADMIN_VIEW` z `object_ref`
  (`admin:tiles`, `tile:<kod>`, `user:<login>`, …) — kto oglądał panel, też jest zapisane.
- Model odczytu to jawny T-SQL na `JdbcTemplate` (`JdbcAdminQueries`), sprawdzany na
  prawdziwym SQL Serverze w `JdbcAdminQueriesIT` (Testcontainers). Repozytoria Spring Data
  i ich atrapy testowe — nietknięte.
- Limiter (120/min na adres) liczy także wywołania panelu; jedno odświeżenie panelu to 2–3 żądania.
- **Faza B** (osobna decyzja po 2–3 tygodniach): nadawanie i odbieranie uprawnień
  z `@Audited`, ochrona CSRF przez istniejący `SameOriginRequestFilter` + wymóg JSON,
  reguła nietykalności kafelka `administracja`. Po Fazie B: zdjęcie `db_datawriter`
  z kont roboczych administratorów (jedyny ich zapis to `tile_permissions`).

## Warianty odrzucone

| Wariant | Dlaczego nie |
|---|---|
| Odczyt + zapis w jednej paczce | dwukrotnie większa paczka bez kompilacji u generatora; generator SQL pokrywa potrzebę od pierwszego dnia; po kilku tygodniach wiadomo, czego naprawdę potrzebuje zapis |
| Osobna rola „admin" w bazie | drugi mechanizm uprawnień obok `tile_permissions`; kafelek jest rolą |
| Widoki SQL zamiast endpointów | Piotr nie chce SSMS — to był powód |

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
