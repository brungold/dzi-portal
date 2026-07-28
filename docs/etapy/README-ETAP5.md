# Etap 5 — paczka: zbiory danych (XLSX → walidacja → merge, Tabulator, edycja z @Version)

```
kafelek REPORT ──klik──> dataset.html?code=licencje
                            │ GET /api/datasets/{code}  (kolumny + wiersze + canEdit)
                            ▼
                        Tabulator (lib lokalnie, zero CDN)
   edycja komórki ──PATCH {columnCode, value, version}──> 200 (wersja+1) | 409 (przegrany wyścig)
   Import XLSX ────POST multipart──> POI → staging w pamięci → walidacja
                                         │ błędy? → 422 + PEŁNY raport, zero zapisów
                                         ▼
                              @Transactional merge (UPSERT po kluczu)
                              → 200 + raport: inserted/updated/unchanged/missingInFile
```

## Decyzje projektowe (sedno etapu)

**Model: agregat Data JDBC zamiast kolumny JSON.** `DatasetRow` (@Version) + dzieci
`DatasetCell` (@MappedCollection) — wiersz i komórki zapisują się i WERSJONUJĄ razem.
Zero zależności od Jacksona w modelu (Boot 4 = Jackson 3, API świeżo przemeblowane —
świadomie nie stawiamy na nim fundamentu), proste SQL-e, a wymagany optimistic locking
to dosłownie mechanizm frameworka: przegrany `save()` rzuca
`OptimisticLockingFailureException` → GlobalExceptionHandler → **409**. Double w testach
odwzorowuje ten kontrakt 1:1 (`InMemoryDatasetRowRepository`).

**Wartości kanoniczne jako tekst** (`ColumnType`): NUMBER z kropką (import przyjmuje
"1 200,50"), DATE ISO (przyjmuje dd.MM.yyyy), BOOL true/false (przyjmuje tak/nie).
Merge porównuje stringi — deterministycznie.

**Semantyka importu:** wszystko albo nic (jeden błąd = 422 z raportem wiersz-po-wierszu,
zero zapisów, ale odrzucony import LĄDUJE w historii `dataset_imports` z raportem);
plik jest źródłem prawdy — UPSERT po kluczu nadpisuje ręczne korekty; wierszy
nieobecnych w pliku NIE usuwamy (raportujemy `missingInFile`); merge w jednej transakcji —
równoległa edycja w trakcie ⇒ rollback całości i 409.

**Uprawnienia PRZEZ kafelek REPORT** (`tiles.action_ref` = kod zbioru): READ = podgląd,
EDIT = edycja + import. Zbiór bez kafelka = niewystawiony (zawsze 403). Kontrola w fasadzie
(mapowanie zbiór→kafelek wymaga zapytania), egzekutor ten sam co wszędzie: `AccessFacade`.

## Pliki

**Zmienione (5):** `portal-api/pom.xml` (+POI 5.3.0 — jawna wersja, BOM Boota nie zarządza) ·
`application.yml` (limity multipart 10 MB) · `GlobalExceptionHandler` (+409 optimistic lock) ·
`frontend/portal-bootstrap.js` (v3: REPORT → dataset.html) · test `InMemoryTilesRepositories`
(+EDIT admina na raport-licencje)

**Nowe (22):** `V3__datasets.sql` · moduł `datasets/` (11 klas) · `V102` seed zbioru licencje ·
`frontend/dataset.html` · `frontend/lib/tabulator/` (Tabulator **6.5.2**, MIT, zvendorowany
z npm — zero CDN) · `docs/przyklady/licencje-import.xlsx` (gotowy plik demo) · 5 plików testów

## Plan commitów (kontynuacja)

| # | Commit |
|---|--------|
| 22 | `feat(db): V3 — schemat zbiorów danych (agregat wiersz+komórki)` |
| 23 | `feat(datasets): typy, parser XLSX, fasada merge/edycji + testy` |
| 24 | `feat(datasets): REST — widok, import 200/422, PATCH z 409 + slice test` |
| 25 | `feat(db): dev-seed zbioru licencje + przykładowy plik importu` |
| 26 | `feat(frontend): dataset.html na Tabulatorze (vendored) + bootstrap v3` |

## Demo (2 minuty)

1. Start api (dev) → kafelek **Raport licencji** → tabela z 2 wierszami seedu.
2. Kliknij komórkę „Posiadane", wpisz `1 300,5` → zapis, wersja 0→1, w audycie
   `DATASET_EDIT` z `object_ref = dataset:licencje:row:N`.
3. **Test wyścigu:** otwórz drugą kartę, zmień tę samą komórkę w obu → druga dostaje
   czerwony komunikat 409 „odśwież widok".
4. **Import XLSX** → `docs/przyklady/licencje-import.xlsx`: raport „dodane 3, zmienione 2,
   w bazie a nie w pliku: 0". Zepsuj plik (wpisz „abc" w Posiadane) → 422 z listą błędów
   per wiersz, nic nie zapisane, odrzucony import w `dataset_imports`.
5. Jako `viewer`: tabela tylko do odczytu (bez przycisku importu, komórki bez edytora),
   a ręczny PATCH curl-em → twarde 403.

## Progi rewizji (zapisane też w kodzie)

- staging w pamięci → tabela staging + strumieniowy SAX przy plikach **>50 tys. wierszy**,
- merge wiersz-po-wierszu przez repozytorium → batch przy tej samej skali,
- cały zbiór w jednej odpowiedzi → paging/wirtualizacja Tabulatora przy **>10 tys. wierszy**.

## Zastrzeżenia

1. POI 5.3.0 przypięte na sztywno — sprawdź nowszy patch linii 5.x.
2. Formuły w XLSX są ewaluowane (FormulaEvaluator); egzotyczne funkcje mogą się nie policzyć —
   wtedy błąd waliduje się jak zwykła zła wartość, plik odrzucony z raportem.
3. Edytor `date`/`tickCross` Tabulatora wysyła wartości, które kanonizuje backend —
   jedno źródło prawdy o formatach; przy dziwnych locale przeglądarki decyduje i tak serwer.

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
