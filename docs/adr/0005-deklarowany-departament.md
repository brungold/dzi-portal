# ADR-0005: Deklarowany departament (bez rejestru użytkowników)

Status: zaakceptowany · Data: 2026-08-03 · Zmienia model przynależności z ADR-0003

## Kontekst

ADR-0003 wprowadził tryb `declared`: klient deklaruje wyłącznie login, a przynależność
wyprowadza serwer z tabeli `user_departments`. Model zakładał wdrożenie w skali jednego
departamentu. W trakcie wdrożenia (03.08.2026) zapadła decyzja o docelowym zasięgu:
**centrala (~4 tys. osób), perspektywicznie cała organizacja (~11,5 tys.)**, z rozrostem
na kolejne departamenty (DZI → DAG, DPB, …).

Przy tej skali rejestr `user_departments` oznacza: prowizję tysięcy wierszy, śledzenie
zatrudnień, odejść i migracji między departamentami oraz cykliczne odświeżenia — koszt
operacyjny nie do przyjęcia dla 2-osobowego utrzymania. Jednocześnie warunki wdrożenia
są jednoznaczne: **sieć wewnętrzna jednostki (monitorowana), portal jako ułatwienie
pracy, ZAKAZ danych wrażliwych za kafelkami.**

Integracja z katalogiem w runtime (wariant A) pozostaje niedostępna organizacyjnie
(ADR-0003, bez zmian).

## Decyzja

**1. Klient deklaruje login ORAZ departament.** Dwa nagłówki:
`X-Auth-User: maciej.mysliwiec`, `X-Auth-Dept: dzi`. Skrót departamentu = wartość
`extensionattribute12` z AD (zweryfikowana spójność na kontach DZI i DPB), odczytywana
przez skrypt kliencki z konta zalogowanego użytkownika — użytkownik niczego nie wpisuje.
W przeglądarce: okno deklaracji z dwoma polami. Serwer normalizuje oba do małych liter;
departament w złym formacie (spacje, polskie znaki) jest ignorowany z ostrzeżeniem
w logu — żądanie przechodzi z samym loginem.

**2. Zbiór uprawnień żądania = {login, departament, "wszyscy"}**, porównywany
z `tile_permissions.ad_group` (AccessFacade, bez zmian). W kolumnie `ad_group` wolno
mieszać skróty departamentów i loginy:

| Zamysł | Wiersze `tile_permissions` |
|---|---|
| cały departament | `'dzi'` |
| kilka departamentów | `'dzi'`, `'dag'` |
| departament + osoby imiennie | `'dag'`, `'maciej.mysliwiec'`, `'zwonik.piotr'` |
| tylko imiennie | `'maciej.mysliwiec'` |
| kafelek publiczny | `'wszyscy'` |

**3. Żadnego rejestru użytkowników po stronie portalu.** Serwer nie pyta o przynależność
ani katalogu, ani bazy. `DeclaredDbGroupResolver` usunięty. Tabela `user_departments`
i migracja V4 **zostają w schemacie nieużywane** (checksumy Flyway + droga powrotna).
`deploy/declared/export-user-departments.ps1` zostaje w repo jako narzędzie nieaktywnego
wariantu.

**4. Decyzję o dostępie nadal podejmuje wyłącznie serwer.** To nie jest powrót do
odrzuconego w ADR-0003 modelu „verify po stronie klienta": klient niczego nie
rozstrzyga, przesyła deklarację, a serwer egzekwuje `tile_permissions` na każdym
zasobie `/api` (403 + DENIED w audycie przy braku uprawnienia).

## Konsekwencje — nazwane wprost

- **Kafelek jest widoczny dla każdego, kto zna adres portalu i wpisze właściwy skrót
  departamentu.** `tile_permissions` PORZĄDKUJE widoczność (kto co widzi domyślnie),
  nie chroni danych. Uprawnienia imienne również są deklaracją (wystarczy znać login).
- **`wszyscy` zmienia znaczenie**: poprzednio — każdy login obecny w rejestrze; teraz —
  dosłownie każdy, kto dotrze do portalu. Kafelek z `'wszyscy'` jest publiczny w sieci
  wewnętrznej.
- **Fail-closed z ADR-0003 przestaje istnieć** (nie ma „nieznanych" loginów). Granicą
  pozostają: zaufane źródła (loopback/CIDR — 401 spoza), limiter (120/min z adresu,
  blokada adresu deklarującego >3 różne loginy w 10 min) i pełny audyt każdej
  deklaracji: login, departament, adres, zasób, decyzja.
- **Zakaz danych osobowych, finansowych i kadrowych za kafelkami — bezwzględny.**
  Dotyczy też treści zbiorów danych (DATASET) i wyników skryptów (SCRIPT).
- Audyt odpowiada na pytanie „co zadeklarowano i skąd", nie „kto siedział przy
  klawiaturze" — jak w ADR-0003, tylko szerzej (departament też jest deklaracją).

## Progi rewizji

- **Pierwszy kafelek z danymi, których nie powinien widzieć każdy** → STOP: wariant A
  (Kerberos/LDAP) albo model z uwierzytelnieniem (hasła lokalne/mTLS z ADR-0003 §odłożone).
- **Incydent podszycia z realną szkodą** → przegląd audytu + eskalacja rozmowy
  o wariancie A z kierownictwem (argument: działający system, skala, udokumentowana granica).
- **Zgoda na konto serwisowe LDAP + SPN** → powrót do wariantu A: profil `prod`,
  bez zmian w kodzie kafelków i uprawnień (`tile_permissions` czytają wtedy grupy AD).

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
