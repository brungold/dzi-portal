# apps/ — moduły aplikacyjne portalu (za strażnikiem, ADR-0007)

Każdy podkatalog = jeden moduł = jeden kafelek. Nazwa podkatalogu MUSI być równa
`tiles.code` — to jedyny łącznik między URL-em a uprawnieniami.

W repo trzymamy KOD modułów. **Danych (CSV/XLSX/JSON z treścią produkcyjną) nie
commitujemy NIGDY** — repo jest publiczne; dane jadą przez J: prosto na serwer.
`.gitignore` blokuje `apps/**/*.csv`, `*.xlsx`, `apps/*/data/`, `apps/*/dane*/`
oraz pliki `_v1`, `_old` — to siatka, nie zwolnienie z myślenia.

## Moduły w produkcji (stan bazy 2026-09-08)

| `tiles.code` | order | pliki w repo | dane na serwerze (poza repo) |
|---|---|---|---|
| `red-pisma-sprawy` | 10 | `index.html`, `app.js`, `module.css` (+ agregaty — patrz uwaga) | CSV 165 MB, `data/` z plikami per KO |
| `m365_copilot_instrukcja_instalacji` | 20 | `index.html`, `app.js`, `module.css`, 6 × PNG | brak |
| `epo-podpis` | 30 | `index.html`, `app.js`, `module.css` | brak (pliki EPO czyta przeglądarka użytkownika) |
| `analiza_obecnosci_raporty_AUREA` | 40 | `index.html`, `app.js`, `module.css`, `*.txt` | `dane_Aurea/` (CSV per departament + `lista_plikow.json`) |

Uwaga do ReD: pliki agregatów (`red-dashboard.js`, `data/*.js`) są generowane
skryptem z CSV — do repo idzie skrypt, nie wynik. Stan serwera vs repo dla tego
modułu: do porównania po listingu (otwarte).

## Konwencje modułu — obowiązkowe

1. **Dane osobno od kodu.** Pliki danych w podkatalogu `data/` (lub `dane*/`).
   Strażnik audytuje jako `APP_DATA` pliki `csv/xlsx/json` (od paczki kodu #2 także
   `pdf/xml/txt` i **cały katalog `data/` niezależnie od rozszerzenia**). Dane
   osobowe w pliku `.js` poza `data/` = pobranie **bez śladu w audycie**. Nie robić.
2. **Bez wersji roboczych na serwerze.** `app_v1.js`, `index_old.html` w
   `D:\portal\apps\<code>\` są serwowane każdemu uprawnionemu pod swoim URL-em.
   Od wersji jest git. Praca robocza: katalog poza `/apps` (np. `D:\portal\praca\`),
   do `/apps` trafia wersja końcowa pod docelową nazwą.
3. **Zero CDN.** Biblioteki wyłącznie zvendorowane w katalogu modułu + wiersz
   w `AUTORSTWO.md`.
4. **Powłoka:** ścieżki `../../assets/...` (witryna IIS) działają, bo strażnik
   serwuje moduł pod tym samym URL-em `/apps/<code>/`.
5. **Dane innych departamentów** w module DZI wymagają jawnej podstawy (zgoda
   właściciela danych) — odnotować w opisie kafelka (`tiles.description`) i w ADR.
6. **Nowy login w uprawnieniach** — format weryfikować w `v_audit_log_pl` po
   pierwszym wejściu osoby na portal; literówka = kafelek niewidoczny bez błędu.

## Jak dodać nowy moduł — stała procedura

1. **[DOM]** nowy katalog `apps/<code>/` z plikami modułu (`index.html` jako wejście).
   Commit + push. (Wyjątek przećwiczony 25.08 i 6–7.09: pliki najpierw na serwer —
   wtedy tego samego tygodnia do repo, inaczej repo przestaje być źródłem prawdy.)
2. **[STACJA]** świeży ZIP z GitHuba → J: → **[SERWER #3]** zawartość do
   `D:\portal\apps\<code>\` (Explorer albo Copy-Item). Pliki danych z J: obok,
   do `data/`, bez otwierania Excelem, UTF-8.
3. **[SERWER #2]** rejestracja: `deploy/sql/apps-nowy-modul.sql` (uzupełnić `<code>`,
   nazwę, uprawnienia; `-f 65001` dla polskich znaków). `display_order` co 10.
4. **[STACJA]** test: strona główna pokazuje kafelek uprawnionym; bezpośredni link
   dla nieuprawnionego = 403; wpis `APP_DENIED` w `v_audit_log_pl`.

Bez kroków w IIS. Bez restartu aplikacji (uprawnienia czytane z bazy na żywo).

## Czego tu nie ma

- `frontend/apps/` w witrynie IIS **nie istnieje** (i nie może powstać — druga,
  niechroniona kopia unieważnia strażnika; test 5 wdrożenia).
- Listy modułów pod `/apps/` — katalogiem jest strona główna (RBAC serwerowy).
