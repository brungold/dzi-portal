# ADR-0004: Wariant źródeł bez profilu `demo`

Status: zaakceptowany · Data: 2026-07-22 · Dotyczy: to drzewo (wariant „bez-demo")

## Kontekst

Decyzja właściciela projektu: drzewo przeznaczone na produkcję ma **fizycznie**
nie zawierać elementów trybu demo — niezależnie od tego, że profil nieaktywny
jest martwym kodem. Główna linia (`dzi-portal-komplet-v4`) zachowuje demo
wraz z jego udokumentowaną wartością (`prod,demo` = test Etapu 1 bez bazy).

## Decyzja

Usunięto z drzewa: pakiet `infrastructure/demo` (DemoConfiguration,
DemoAuditWriter, DemoTaskSimulator, DemoTaskStore), trzy lustra seedów
(DemoTilesConfiguration, DemoTasksConfiguration, DemoDatasetsConfiguration),
`application-demo.yml` oraz `docs/demo-runbook.md`. Żaden kod produkcyjny
tych klas nie importował — usunięcie nie zmienia niczego poza profilem demo.

`AuditWriter` **celowo zachowuje** `@Profile("!demo")`: aktywacja profilu
`demo` na tym wariancie kończy się błędem startu (brak beana zapisu audytu)
— świadome fail-fast zamiast cichego uruchomienia bez trybu, o który proszono.

## Co ŚWIADOMIE zostaje (to nie są „elementy demo")

- kafelki seedowe dev `Demo: błąd skryptu` / `Demo: timeout` i skrypty
  `scripts/demo-*.ps1` — to część seedów **dev** (V100–V102), wykonują je
  prawdziwy worker i PowerShell; służą testom trzech zakończeń zadania,
- wzmianki historyczne w `docs/etapy/README-COMMIT-33-demo.md`, briefie
  i komentarzach — historia projektu się wydarzyła i pozostaje udokumentowana.

## Konsekwencje

1. **Dwa drzewa źródeł.** Każda przyszła poprawka wymaga naniesienia w obu
   (główna linia v4 + ten wariant). Ryzyko rozjazdu kopii — znane i przyjęte
   (odnotowane już przy osobnej kopii demo, §8 briefu).
2. Tracimy na tym drzewie kombinacje `dev,demo` (start bez SQL Express)
   i `prod,demo` (test IIS/Kerberos/LDAP przed powstaniem bazy na serwerze).
   Gdy będą potrzebne — użyć głównej linii v4.
3. Profil `declared` (ADR-0003) pozostaje nietknięty i w pełni funkcjonalny.

## Próg rewizji

Pierwszy przypadek poprawki naniesionej tylko w jednym z dwóch drzew
(wykryty rozjazd) = sygnał do powrotu do jednej linii z przełącznikiem
profilowym i wycofania tego wariantu.

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
