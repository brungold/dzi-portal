# apps/ — moduły aplikacyjne portalu (za strażnikiem, ADR-0007)

Każdy podkatalog = jeden moduł = jeden kafelek. Nazwa podkatalogu MUSI być równa
`tiles.code` — to jedyny łącznik między URL-em a uprawnieniami.

W repo trzymamy KOD modułów. **Danych (CSV/XLSX z treścią produkcyjną) nie commitujemy
NIGDY** — repo jest publiczne; dane jadą przez J: prosto na serwer.

## Jak dodać nowy moduł — stała procedura

1. **[DOM]** nowy katalog `apps/<code>/` z plikami modułu (`index.html` jako wejście;
   ścieżki do powłoki: `../../assets/...` — na serwerze wciąż działają, bo strażnik
   serwuje moduł pod tym samym URL-em `/apps/<code>/`). Commit + push.
2. **[STACJA]** świeży ZIP z GitHuba → J: → **[SERWER #3]** zawartość do
   `D:\portal\apps\<code>\` (Explorer albo Copy-Item). Pliki danych z J: obok,
   bez otwierania Excelem, UTF-8.
3. **[SERWER #2]** rejestracja: `deploy/sql/apps-nowy-modul.sql` (uzupełnić `<code>`,
   nazwę, uprawnienia; `-f 65001` dla polskich znaków).
4. **[STACJA]** test: strona główna pokazuje kafelek uprawnionym; bezpośredni link
   dla nieuprawnionego = 403; wpis DENIED w `v_audit_log_pl`.

Bez kroków w IIS. Bez restartu aplikacji (uprawnienia czytane z bazy na żywo).

## Czego tu nie ma

- `frontend/apps/` w witrynie IIS **nie istnieje** (i nie może powstać — druga,
  niechroniona kopia unieważnia strażnika; test 5 wdrożenia).
- Biblioteki: wyłącznie vendorowane lokalnie w katalogu modułu (zasada zero-CDN)
  + wiersz w `AUTORSTWO.md`.
