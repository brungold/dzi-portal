# Frontend (statyczny)

Serwowany przez IIS w prod, przez Springa w dev (profil `dev`, katalog `../frontend`).

## Integracja pliku kafelkowego (index.html od autora frontendu)

Dokładnie dwie zmiany w jego pliku:

1. Każdy kafelek dostaje `data-tile-id="<code z tabeli tiles>"`.
2. Przed `</body>`: `<script src="portal-bootstrap.js" defer></script>`.

Wszystko inne (pokazywanie/ukrywanie po uprawnieniach, akcje kliknięć, pasek statusu,
komunikaty błędów) robi `portal-bootstrap.js`. Kafelki bez uprawnień są ukrywane —
to warstwa UX; twarda kontrola (403 + audyt) i tak działa w backendzie.

## Checklist przeglądu pliku przed integracją

- [ ] zero odwołań do CDN (fonty, ikony, skrypty) — wszystko lokalnie w `lib/`,
- [ ] usunięte atrapy `onclick`/`href="#"` generowane przez LLM (bootstrap sam wiąże kliknięcia),
- [ ] kody w `data-tile-id` uzgodnione z tabelą `tiles` (kolumna `code`),
- [ ] plik działa otwarty przez Springa: `http://localhost:8080/index.html`.

## Szybki test bez pliku kolegi

`index.example.html` odwzorowuje seed dev: profil `dev`, przeglądarka →
`http://localhost:8080/index.example.html` (dev-fallback loguje jako `tester`).
Zmiana persony: w `application-dev.yml` ustaw `dev-fallback-user: viewer` i odśwież —
zniknie panel administratora, a Restart ETL zgaśnie (brak EXECUTE).

---

*Autor: Maciej Myśliwiec, 2026.*
