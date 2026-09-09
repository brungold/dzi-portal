# ADR-0008: Departament z jednostki organizacyjnej AD (moduł IIS v3.0)

Status: zaakceptowany · Data decyzji: 2026-08-12 · Spisany: 2026-09-08 · Uzupełnia ADR-0005 i ADR-0006

## Kontekst

ADR-0005 wprowadził departament jako drugi wymiar uprawnień (`X-Auth-Dept`,
zbiór uprawnień żądania = {login, departament, `wszyscy`}) i wskazał jako źródło
skrótu atrybut `extensionattribute12` — wtedy jeszcze deklarowany przez klienta.
ADR-0006 przeniósł ustalanie loginu na moduł IIS po Windows Authentication i na czas
przejściowy **usuwał** `X-Auth-Dept` (moduł v2.0): departament miał „wrócić z modułem v2".

Wrócił 2026-08-12 z modułem **v3.0**, ale z innego źródła niż zapisano w ADR-0005.
Ta decyzja istniała dotąd wyłącznie w komentarzu w `AuthUserHeaderModule.cs`
na serwerze; repo do 2026-09 miało v2.0.

## Decyzja

**1. Źródłem departamentu jest pierwsze `OU=` w `distinguishedName` użytkownika**,
znormalizowane do małych liter: `CN=...,OU=DZI,OU=Biuro,OU=Centrala,DC=zszik,DC=pl` → `dzi`.
Podstawa: próbki DN czterech kont z różnych komórek — u wszystkich departament jest
pierwszym OU po CN. `extensionattribute12` nie jest używany.

**2. Wartość ustala serwer, nigdy klient.** Moduł IIS na `AuthenticateRequest`
usuwa `X-Auth-Dept` przysłany przez klienta, na `PostAuthenticateRequest` wstawia
wartość z AD. Aplikacja (`DeclaredHeaderAuthenticationFilter`) nie rozróżnia źródła —
kontrakt nagłówka z ADR-0005 pozostaje bez zmian.

**3. Odczyt AD tożsamością puli aplikacji, bez konta i hasła.** `DirectorySearcher`
po `sAMAccountName`, `PropertiesToLoad = distinguishedName`, timeout 3 s.
Konsekwencja: system nadal nie ma żadnego sekretu poza hasłem `portal_app` do bazy.

**4. Cache per login w procesie w3wp:** sukces 15 min, porażka 60 s. Zmiana OU
pracownika propaguje się najpóźniej po 15 minutach (albo po recyklingu puli).

**5. Tryb awaryjny: brak nagłówka, nie błąd.** AD niedostępne lub DN bez `OU=` →
`X-Auth-Dept` nie powstaje → kafelki nadane na departament znikają, kafelki imienne
i `wszyscy` działają. Portal nie kładzie się przez AD.

## Konsekwencje

- Uprawnienie `INSERT INTO tile_permissions (..., 'dzi', 'READ')` działa produkcyjnie
  od 2026-08-12 — bez rejestru użytkowników, bez LDAP w aplikacji.
- **Tryb awaryjny jest niemy**: moduł nie ma loggera, aplikacja nie odnotowuje braku
  nagłówka. Zgłoszenie „nie widzę kafelka, wczoraj widziałem" → najpierw
  `/api/whoami` ze stacji (skrót departamentu w `groups`?). Do rozważenia: ostrzeżenie
  w logu aplikacji, gdy żądanie uwierzytelnionego loginu przychodzi bez departamentu
  przez dłużej niż N minut.
- Kompilacja modułu wymaga `/reference:System.DirectoryServices.dll`
  (`deploy/iis/INSTRUKCJA-MODUL-WINDOWS-AUTH.md`).
- Struktura OU staje się częścią kontraktu bezpieczeństwa: reorganizacja drzewa AD
  (np. departament jako drugie OU) zmieni wartości nagłówka **bez żadnego błędu**.
  Próg rewizji: pierwsza migracja kont między OU albo pojawienie się kont, u których
  pierwsze OU nie jest departamentem.

## Warianty odrzucone

| Wariant | Dlaczego nie |
|---|---|
| `extensionattribute12` (jak ADR-0005) | atrybut niestandardowy — wymagałby potwierdzenia, że jest wypełniany dla wszystkich kont i utrzymywany przez zespół AD; OU jest utrzymywane z definicji |
| grupy AD `ZSZIK-Portal-*` (wariant A) | wymaga zakładania grup przez zespół AD dla każdego kafelka — ADR-0005 odrzucił to skalą (4–11,5 tys. użytkowników) |
| LDAP z aplikacji Java | sekret w systemie (konto read-only), natywne bindowanie — ADR-0001 dec. 7; moduł IIS robi to samo tożsamością puli za darmo |

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
