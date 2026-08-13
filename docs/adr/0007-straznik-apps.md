# ADR-0007: Moduły aplikacyjne za strażnikiem uprawnień

Status: zaakceptowany · Data: 2026-08-13 · Uzupełnia ADR-0002, ADR-0006

## Kontekst

`tile_permissions` kontrolowała wyłącznie widoczność kafelka (listę wydaje `/api/tiles`,
tam RBAC działa). Pliki modułów leżały w katalogu witryny i wydawał je IIS, sprawdzając
tylko uwierzytelnienie domenowe — realna bariera to ~11,5 tys. kont, nie lista
uprawnionych. Dotyczyło to również CSV z danymi osobowymi (moduł ReD).

## Decyzja

Pliki modułów wychodzą poza witrynę (`D:\portal\apps\`), a `/apps/**` trafia przez
URL Rewrite do aplikacji. `AppsController` wydaje plik dopiero po `AccessFacade.canRead`
dla kafelka o kodzie równym pierwszemu segmentowi ścieżki — **ta sama tabela, która
decyduje „czy widzisz kafelek", decyduje „czy dostaniesz plik"**. Odmowa = 403 (strona
HTML dla człowieka) + DENIED w audycie. Zasada podziału: dane poza witryną, wygląd
(assets powłoki) w witrynie.

Rozstrzygnięcia szczegółowe:

- **D1 — zakres: całe `/apps/**`** (wariant A). Jeden mechanizm, fail-closed (katalog
  bez kafelka w bazie = 403); nowy moduł to INSERT, nigdy edycja `web.config` na
  produkcji. Moduły poglądowe `gant` i `form_upr` usunięte zamiast obejmowane.
- **D2 — `active=0` znaczy „niedostępny w ogóle"**. Egzekwuje samo zapytanie
  `findForTileAndGroups` (warunek `t.active=1`) — bez nowego kodu i bez trzeciego
  stanu, któremu przeczyłaby nazwa kolumny. Próg rewizji: realna potrzeba modułu
  „działa z linku, ukryty na stronie" ⇒ osobna kolumna, nie przeciążanie `active`.
- **D3 — audyt selektywny** (`AppsAuditPolicy`): wejście do modułu (HTML/katalog),
  pliki danych (csv/xlsx/json) i KAŻDA odmowa/błąd; zasoby towarzyszące (css/js/
  obrazki/fonty) poza rejestrem. Ta sama logika co wyłączenie 304 w ADR-0002 pkt 2:
  szum ukrywa zdarzenia istotne. `/api/*` bez zmian — audyt pełny (`AuditPolicy.ALWAYS`).
- **403, nie 404, dla nieuprawnionych** — spójnie z AccessFacade; ale nieistniejący
  plik/moduł i próba traversalu = 404 bez zdradzania struktury dysku.
- **Wydanie strumieniowe** (`FileSystemResource`) + słaby ETag (rozmiar+mtime);
  304 poza audytem jak dotąd.

## Warianty odrzucone

- **URL Authorization w IIS** — dublowałby listę uprawnionych poza `tile_permissions`,
  bez audytu i bez pojęcia „kafelek".
- **Endpoint wyłącznie na CSV** — chroni dane, zostawia otwarty dashboard; dwa reżimy
  do pamiętania.
- **`@PreAuthorize` na kontrolerze plików** — odmowa wpadałaby w GlobalExceptionHandler
  jako JSON; moduły otwiera przeglądarka, więc strażnik woła fasadę jawnie i zwraca HTML.

## Konsekwencje

- 165 MB CSV płynie teraz przez ARR→Javę; przy skali portalu pomijalne. Zalecana
  zmiana u autora modułu: `cache:'no-cache'` zamiast `no-store` (ETag ⇒ 304).
  Próg rewizji: transfery >1 GB/dzień albo timeouty ARR.
- Nowy moduł wymaga wpisu w `tiles` zanim zadziała — fail-closed z definicji.
- `DevSecurityConfiguration` bez `/apps/**` w permitAll — strażnik testowalny lokalnie.

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
