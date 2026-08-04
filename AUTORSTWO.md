# Autorstwo i komponenty zewnętrzne

## Autor

**Maciej Myśliwiec** — koncepcja, architektura, decyzje projektowe, implementacja
i dokumentacja portalu DZI (2026).

Zakres autorstwa obejmuje w szczególności:

- architekturę systemu (wariant A: IIS + Kerberos jako brzeg uwierzytelniający,
  API na loopbacku, osobny worker wykonujący skrypty z kolejki-tabeli),
- model bezpieczeństwa (trójwarstwowa ochrona nagłówka tożsamości, dwustronny RBAC
  oparty o grupy AD, whitelist skryptów, audyt append-only egzekwowany uprawnieniami SQL),
- model **deklarowanej tożsamości** dla wdrożeń bez integracji z katalogiem
  (ADR-0003; iteracja ADR-0005 — deklarowany departament dla skali tysięcy
  użytkowników, ze świadomie nazwanymi granicami): rozdzielenie deklaracji od autoryzacji — klient deklaruje wyłącznie
  login, przynależność i uprawnienia wyprowadza serwer; odrzucenie kontroli
  po stronie klienta; kompensacje (fail-closed, limiter wykrywający deklarowanie
  wielu loginów z jednego adresu, podwójny przełącznik otwarcia na sieć)
  oraz jawne wyznaczenie granic zastosowania tego trybu,
- projekt schematu bazy danych i strategię migracji (Flyway, jeden właściciel schematu),
- mechanizm kolejki zadań (atomowy claim: `UPDLOCK, READPAST` + `OUTPUT`),
- model danych zbiorów (agregat wiersz+komórki, optimistic locking przez `@Version`),
- mechanizm odświeżania (token stanu jako ETag, odpowiedzi 304 poza audytem),
- procedury wdrożeniowe i utrzymaniowe (deploy z automatycznym rollbackiem, hardening,
  retencja rejestrów poza tożsamościami runtime),
- decyzje architektoniczne udokumentowane w `docs/adr/`.

## Komponenty zewnętrzne (nie są utworem autora)

| Komponent | Licencja | Lokalizacja / sposób użycia |
|---|---|---|
| **Tabulator** 6.5.2 | MIT | `frontend/lib/tabulator/` — biblioteka zvendorowana (skopiowana z npm), **bez zmian**; nagłówki autorstwa świadomie **nieumieszczone** |
| Spring Boot 4.1, Spring Framework, Spring Security, Spring Data JDBC | Apache 2.0 | zależności Maven |
| Apache POI 5.3.0 | Apache 2.0 | zależność Maven (import XLSX) |
| Flyway | Apache 2.0 | zależność Maven (migracje) |
| Microsoft JDBC Driver for SQL Server | MIT | zależność Maven (runtime) |
| Lombok | MIT | zależność Maven (kompilacja) |
| Testcontainers, JUnit 5, AssertJ | MIT / EPL 2.0 | zależności testowe |
| WinSW | MIT | narzędzie zewnętrzne, opisane w `deploy/winsw/README-WinSW.md`; binarium nie jest częścią repozytorium |

## Wyjątek: migracje Flyway BEZ nagłówków (świadomie)

Pliki `portal-api/src/main/resources/db/migration/**` oraz `db/migration-dev/**`
**nie zawierają** nagłówka z informacją o autorstwie — i nie wolno go tam dodawać.

Flyway liczy sumę kontrolną (checksum) z treści pliku migracji i zapisuje ją w tabeli
`flyway_schema_history` przy pierwszym zastosowaniu. Migracja raz zastosowana jest
**niezmienna**: dopisanie choćby komentarza zmienia checksum, a przy kolejnym starcie
walidacja Flyway przerywa uruchomienie aplikacji komunikatem
`Migration checksum mismatch`. Nagłówek autorstwa w tych plikach oznaczałby awarię
każdego środowiska, na którym schemat już istnieje.

Autorstwo schematu bazy jest udokumentowane w tym pliku oraz w `docs/adr/`.

Skrypty w `deploy/sql/**` (dev-setup, prod-grants, audit-retention) **nie są**
zarządzane przez Flyway — nagłówki w nich pozostają.

Plik `docs/przyklady/licencje-import.xlsx` jest binarny — informacja o autorstwie
nie została w nim osadzona w treści; można ją dodać we właściwościach dokumentu
(Plik → Informacje → Autor).

## Prawa

Autorskie **prawa osobiste**, w tym prawo do oznaczenia utworu nazwiskiem autora,
są niezbywalne i nieograniczone w czasie — art. 16 ustawy z dnia 4 lutego 1994 r.
o prawie autorskim i prawach pokrewnych. Przysługują autorowi niezależnie od tego,
komu przysługują prawa majątkowe.

Zakres autorskich **praw majątkowych** regulują odrębne przepisy i ustalenia
z pracodawcą (por. art. 12 i art. 74 ust. 3 ww. ustawy dotyczące utworów oraz
programów komputerowych stworzonych w ramach stosunku pracy). Niniejszy plik nie
rozstrzyga tej kwestii i nie stanowi opinii prawnej.

Nagłówki z informacją o autorstwie umieszczone w plikach źródłowych nie mogą być
usuwane ani modyfikowane przy kopiowaniu, dalszym rozwijaniu ani rozpowszechnianiu
kodu.