# portal-api jako usługa Windows — runbook (Faza 10, wdrożona 2026-09-01)

Portal jest usługą Windows (`portal-api`) opakowaną przez WinSW 2.x: Windows nadzoruje
WinSW jak każdą usługę, WinSW uruchamia i pilnuje Javy. Zamknięcie okna RDP,
wylogowanie, restart serwera — portal wraca sam (`Automatic (Delayed)`, restart po
awarii 10 s / 30 s, reset licznika po godzinie).

Konfiguracja: `deploy/winsw/portal-api.declared.xml` (= `D:\portal\api\portal-api.xml`).
Hasło do bazy: wyłącznie `D:\portal\api\config\application-declared.yml`
(szablon `deploy/declared/application-declared.yml.example`).

## Operacje [SERWER #3 — PowerShell jako administrator]

| Zadanie | Komenda |
|---|---|
| stan usługi | `Get-Service portal-api \| Select-Object Name, Status, StartType` |
| health aplikacji | `Invoke-RestMethod http://127.0.0.1:8080/actuator/health` |
| restart (po zmianie `config\application-declared.yml`) | `Restart-Service portal-api` |
| wdrożenie nowego jara | `Stop-Service portal-api` → stary jar do `D:\portal\archiwum\portal-api-<data>.jar` → nowy jako `portal-api.jar` → `Start-Service portal-api` → health (albo `deploy/deploy-api.ps1`, po przeczytaniu) |
| log — ostatnie linie | `Get-Content D:\portal\api\portal-api.out.log -Tail 30 -Encoding UTF8` |
| log na żywo | `Get-Content D:\portal\api\portal-api.out.log -Wait -Tail 30 -Encoding UTF8` |
| log wrappera (start/stop/awarie usługi) | `Get-Content D:\portal\api\portal-api.wrapper.log -Tail 30` |
| usunięcie usługi (rollback do trybu ręcznego) | `Stop-Service portal-api` → `& D:\portal\api\portal-api.exe uninstall` |

Tryb ręczny (`java -jar` z konsoli) nadal działa, ale **nie równolegle** z usługą —
obie instancje biją się o port 8080. Rotacja logów: 10 MB, 8 plików wstecz.

## Zegary zamknięcia

`Stop-Service` → WinSW wysyła Javie Ctrl+C → Spring przestaje przyjmować żądania
i czeka na te w toku najwyżej **20 s** (`spring.lifecycle.timeout-per-shutdown-phase`)
→ WinSW zabija proces po **30 s** (`stoptimeout`). Kolejność 20 < 30 jest celowa
(ADR-0002 dec. 6). Poprawne zamknięcie widać w logu jako
`Commencing graceful shutdown` → `Graceful shutdown complete` → `HikariPool-1 - Shutdown completed`
(potwierdzone 2026-09-01: 16 ms bez żądań w toku).

## Testy odbiorowe

| Test | Stan |
|---|---|
| B — `Restart-Service` → health UP | ✔ 2026-09-01 |
| C — wylogowanie z RDP, usługa dalej Running | ✔ 2026-09-01 |
| D — `Stop-Service` → linie graceful w logu | ✔ 2026-09-01 |
| A — przeglądarka ze stacji po przejściu na usługę | do wykonania z biura |
| Faza 12 — restart serwera, auto-start, `BackConnectionHostNames` (KB896861) | do wykonania rano, na miejscu |

## Świadome kompromisy

- **LocalSystem** zamiast dedykowanego konta. Do bazy loguje się konto SQL `portal_app`,
  więc tożsamość Windows nie jest potrzebna. Zawężenie (`NT AUTHORITY\NetworkService`
  + `icacls`: zapis `D:\portal\api` na logi, odczyt `D:\portal\apps`) — backlog, nie pilne.
- **`-Xmx1024m`**: CSV 165 MB jest wydawany strumieniowo (`FileSystemResource`), nie
  ładuje się do pamięci JVM. Próg rewizji: `OutOfMemoryError` w logu.
- **`delayedAutoStart`**: portal czeka na SQL Server po restarcie systemu — kosztem
  kilkudziesięciu sekund.

## Pułapki

- WinSW 2.x: komendy **bez myślników** (`version`, nie `--version`).
- Dysk J: niewidoczny w sesji administratora — pliki z J: kopiować z konsoli zwykłej (#2).
- `Get-Content` bez `-Encoding UTF8` krzaczy polskie znaki (PS 5.1); plik jest poprawny.
- Server Manager po wylogowaniu pokazuje czerwone „1 Services" — to `cbdhsvc`
  (Clipboard User Service, per sesja), nie portal.
- `<name>` w `portal-api.xml` potrafi wyświetlać się w oknach czatu jako `<n>` —
  plik z serwera jest poprawny; kopie z czatu porównać (`fc.exe`) przed użyciem.

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
