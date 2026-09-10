# apps/ — moduły aplikacyjne portalu (za strażnikiem, ADR-0007)

**Kod modułów kafelków żyje wyłącznie na serwerze: `D:\portal\apps\<kod>\`.**
Decyzja z 2026-09-08 — repo nie zawiera katalogów modułów (`.gitignore`: `apps/*/`).
Jedyny wyjątek: `apps/administracja/` (panel administracyjny) — to część portalu sprzężona
z jarem (`/api/admin/**`), więc jest wersjonowana razem z nim (ADR-0009).
Ten plik to procedura, układ katalogów i konwencje. Konsekwencja do zapamiętania:
kopia zapasowa `D:\portal\apps` jest jedynym zabezpieczeniem modułów.

Każdy podkatalog = jeden moduł = jeden kafelek. Nazwa podkatalogu MUSI być równa
`tiles.code` — to jedyny łącznik między URL-em a uprawnieniami.

## Moduły w produkcji (stan bazy 2026-09-08)

| `tiles.code` | order | pliki | dane |
|---|---|---|---|
| `red-pisma-sprawy` | 10 | `index.html`, `app.js`, `module.css` | `data\` (agregaty per KO, generowane skryptem) |
| `m365_copilot_instrukcja_instalacji` | 20 | `index.html`, `app.js`, `module.css`, 6 × PNG | brak |
| `epo-podpis` | 30 | `index.html`, `app.js`, `module.css` | brak (pliki EPO czyta przeglądarka użytkownika) |
| `analiza_obecnosci_raporty_AUREA` | 40 | `index.html`, `app.js`, `module.css` | `dane_Aurea\` (CSV per departament + `lista_plikow.json`) |
| `administracja` | 900 | `index.html`, `app.js`, `module.css` — **w repo** (`apps/administracja/`, ADR-0009) | brak (wszystko z `/api/admin/**`) |

## Układ katalogów — jedna zasada

`D:\portal\apps\<kod>\` to witryna: strażnik wyda **każdy** plik z tego katalogu każdej
osobie z uprawnieniem do kafelka — także plik, o którym autor zapomniał. Test dla
każdego pliku: *„czy przeglądarka potrzebuje tego, żeby wyświetlić moduł?"* Nie → nie tu.

```
D:\portal\apps\<kod-kafelka>\          SERWOWANE — tylko to, co przeglądarka pobiera
    index.html                          wejście do modułu
    app.js                              kod modułu
    module.css                          style modułu
    img\                                ikony, zrzuty ekranu (opcjonalnie)
    data\                               WSZYSTKIE pliki danych, jakiekolwiek rozszerzenie
        <stała-nazwa>.json / .csv / .js   (każde pobranie = wpis APP_DATA w audycie)
        details\ ...                    podkatalogi dowolne

D:\portal\praca\<kod-kafelka>\         NIESERWOWANE — tylko przez RDP, tylko administratorzy
    zrodla\        surowe eksporty z systemów źródłowych (np. 279 MB JSON)
    skrypty\       generatory (generate-red-ko.py), SQL do pobierania danych, notatki techniczne
    robocze\       wersje _v1/_v2, próbne index.html, migawki do porównania
    archiwum\      poprzednie wydania z datą w nazwie (zamiast _old w witrynie)
```

## Konwencje — obowiązkowe

1. **Dane wyłącznie w `data\`.** Nie w głównym katalogu modułu, nie w plikach `.js` obok
   `app.js`. Audyt rozpoznaje dane po rozszerzeniu (`csv/xlsx/json`); po paczce kodu #2
   cały katalog `data\` jest audytowany niezależnie od rozszerzenia (aneks ADR-0007).
   Dane osobowe w `.js` poza `data\` = pobranie bez śladu. Nie robić.
2. **Stałe nazwy plików danych.** `app.js` odwołuje się do `data\sprawy.json`, nie do
   `data\20260827_sprawy.json`. Nowy eksport = nadpisanie pod tą samą nazwą; kopia z datą
   → `praca\archiwum\`. Inaczej każda aktualizacja to edycja kodu.
3. **Zero wersji roboczych w witrynie.** `_v1`, `_v2`, `_old`, migawki `data\xxx_20260817` —
   do `praca\robocze\` albo kasacja. Są serwowane pod swoim URL-em każdemu uprawnionemu.
4. **Źródła i skrypty poza witryną.** Generator czyta z `praca\zrodla\`, pisze do
   `apps\<kod>\data\`. Skrypt SQL opisujący, skąd biorą się dane, nie jest dla użytkowników.
5. **Notatki techniczne** (`INSTRUKCJA.txt`, `INFORMACJA_O_POPRAWCE.txt`) — `praca\`.
   W witrynie tylko treść dla użytkownika, i wtedy jako HTML w module.
6. **Dane osobowe innych komórek** — podstawa (zgoda właściciela danych) przed wdrożeniem,
   wzmianka w opisie kafelka (`tiles.description`).
7. **Zero CDN.** Biblioteki wyłącznie lokalnie w katalogu modułu.
8. **Powłoka:** `../../assets/css/portal-dzi.css` i `../../assets/js/portal-dzi.js`
   (witryna IIS) — działają, bo strażnik serwuje moduł pod `/apps/<kod>/`.
9. **Nowy login w uprawnieniach** — format weryfikować w `v_audit_log_pl` po pierwszym
   wejściu osoby na portal; literówka = kafelek niewidoczny bez błędu.

## Jak dodać nowy moduł — procedura

1. **[SERWER #3]** katalog `D:\portal\apps\<kod>\` z plikami modułu (układ wyżej).
   Dane do `data\`, przez J: (kopiowanie z J: tylko w konsoli nieadminowej), bez Excela, UTF-8.
2. **[SERWER #2]** rejestracja: `deploy/sql/apps-nowy-modul.sql` (uzupełnić `<code>`,
   nazwę, uprawnienia; `-f 65001` dla polskich znaków). `display_order` co 10.
3. **[STACJA]** test: strona główna pokazuje kafelek uprawnionym; bezpośredni link
   dla nieuprawnionego = 403; wpis `APP_DENIED` w `v_audit_log_pl`.
4. Wiersz w tabeli wyżej (ten plik) — żeby repo wiedziało, co stoi na serwerze.

Bez kroków w IIS. Bez restartu aplikacji (uprawnienia czytane z bazy na żywo).

## Czego tu nie ma

- `frontend/apps/` w witrynie IIS **nie istnieje** (i nie może powstać — druga,
  niechroniona kopia unieważnia strażnika).
- Listy modułów pod `/apps/` — katalogiem jest strona główna (RBAC serwerowy).
