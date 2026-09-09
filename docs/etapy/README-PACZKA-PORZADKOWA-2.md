# Paczka porządkowa #2 (2026-09-09) — komentarze, etykiety, martwe pliki

Cel: kod i dokumentacja mówią to samo, co serwer. **Jedna zmiana w Javie poza komentarzami**
(zdjęcie `@Profile("!demo")` z `AuditWriter`) — reszta to komentarze, banery i usunięcia.
Nie wymaga wdrożenia jara. Wymaga `mvn clean verify` (przez tę jedną linię i przez
pewność, że komentarze nie zepsuły składni).

Zakłada, że paczka 1 („repo = serwer") jest już w repo — README, brief i `.gitignore`
w tej paczce są jej nowszymi wersjami i ją nadpisują.

## Tabela zmian

| Plik | Zmiana |
|---|---|
| `portal-common/.../audit/AuditWriter.java` | usunięte `@Profile("!demo")` + import (resztka po ADR-0004) — **jedyna zmiana kodu** |
| `.../security/DeclaredHeaderAuthenticationFilter.java` | javadoc: profil produkcyjny, nagłówki od modułu IIS, departament z OU, limiter liczy `/apps` |
| `.../security/DeclaredIdentityProperties.java` | javadoc: OU zamiast `extensionattribute12` |
| `.../security/AdGroupResolver.java`, `PortalUser.java`, `PortalSecurityProperties.java`, `WhoAmIController.java`, `tiles/AccessFacade.java`, `web/SameOriginRequestFilter.java` | komentarze: Kerberos/LDAP/grupy AD → NTLM + moduł, {login, departament, wszyscy} |
| `LdapAdGroupResolver`, `LdapConfiguration`, `LoopbackHeaderAuthenticationFilter`, `PortalLdapProperties`, `CachingAdGroupResolver`, `StaticAdGroupResolver` | etykieta w javadocu: „WARIANT A — nieużywany, droga powrotna, nie rozwijać" |
| `SecurityConfig.java`, `DevSecurityConfiguration.java` | javadoc: dlaczego ich łańcuchy żyją także pod `declared` i co trzeba zmienić jednocześnie |
| `application-prod.yml` | baner „WARIANT A — nieużywany" |
| `docs/adr/0001`, `0003`, `0005` | notka „Aktualizacja 2026-09" pod statusem (historia nietknięta) |
| `docs/etap1-runbook.md`, `etap6-runbook.md`, `deklaracja-runbook.md` | banery: historyczny / dev-awaryjny, odsyłacze do aktualnych dokumentów |
| `deploy/iis/README-IIS.md` | v3.0, reguła `/apps`, XFF od ARR, moduły poza witryną |
| `deploy/iis/setup-iis.ps1`, `verify-etap1.ps1`, `deploy/verify-hardening.ps1`, `register-audit-retention.ps1`, `deploy/declared/portal-client.ps1`, `deploy/sql/audit-retention.sql`, `deploy/winsw/*.template` | banery „wariant A / nieużywane / backlog" |
| `README.md` | sekcja **Mapa profili**, decyzja o modułach poza repo, pkt 7 „Znane punkty uwagi", historia |
| `docs/BRIEF-PROJEKTU.md` | moduły poza repo, test A/403 wykonane, porządki Piotra |
| `apps/README.md` | przepisany: moduły tylko na serwerze, **układ katalogów** (serwowane / nieserwowane), 9 konwencji, procedura |
| `frontend/README.md` | nowa treść: co jest czym w witrynie |
| `.gitignore` | `apps/*/` z wyjątkiem `apps/README.md`; siatka na dane (`*.json` z wyjątkiem `*.przyklad.json`) |

## Do usunięcia z repo (nie ma ich w paczce — `git rm`)

- `frontend/index.example.html`, `frontend/portal-bootstrap.js` — stara integracja, martwe od commitu 34/37.
- `apps/red-pisma-sprawy/` (3 pliki z 13.08) — spójnie z decyzją „moduły tylko na serwerze";
  na serwerze moduł jest nowszy, kopia w repo wprowadzała w błąd. Historia zostaje w gicie.

## Kroki [DOM]

1. `git status` czysty; paczka 1 już scommitowana.
2. Rozpakować **zawartość** katalogu `paczka-porzadkowa` na repo (`Copy-Item "$paczka\*" -Recurse -Force`).
3. `git rm frontend/index.example.html frontend/portal-bootstrap.js`
   `git rm -r apps/red-pisma-sprawy`
4. `mvn clean verify` — spodziewane: wszystkie dotychczasowe testy zielone (żadnych nowych;
   zmiany w Javie to komentarze + jedna adnotacja). Przy FAIL: log tu, nie commitować.
5. `git add -A` → `git status` — spodziewane ok. 35 plików zmienionych, 5 usuniętych, 0 nowych
   poza `README-PACZKA-PORZADKOWA-2.md` (ten plik można od razu przenieść do `docs/etapy/`).
6. Commit: `chore: porzadki — komentarze pod stan faktyczny (NTLM+modul, OU), etykiety wariantu A, mapa profili, martwe pliki, moduly poza repo`
7. Push. **Bez wdrażania** — jar poczeka na panel.

## Walidacja przed spakowaniem (wykonana)

- Bilans klamer w 17 plikach Java: równy; `AuditWriter` bez śladu `Profile`.
- YAML (`application-prod.yml`) i XML (dwa szablony WinSW) parsują się.
- Brak loginów osób trzecich, haseł, adresów IP w nowych treściach.
- Nie dotknięto plików z paczki kodu #2 (`AppsAuditPolicy`, `AppsController`,
  `DeclaredSecurityConfiguration`, `application.yml`, `application-declared.yml`,
  `AppsControllerTest`, ADR-0007) — paczki nie kolidują.
