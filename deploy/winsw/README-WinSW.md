# WinSW — portal-api jako usługa Windows

**Stan produkcyjny (Faza 10, wdrożona 2026-09-01):** usługa `portal-api` na WinSW 2.x,
profil `declared`, LocalSystem, plik `deploy/winsw/portal-api.declared.xml` = kopia
`D:\portal\api\portal-api.xml` z serwera (bez sekretów — hasło do bazy żyje
w `config\application-declared.yml`, szablon: `deploy/declared/application-declared.yml.example`).
Eksploatacja (restart, wdrożenie jara, logi, rollback): `docs/winsw-runbook.md`.

Szablon `portal-api.xml.template` opisuje **wariant A** (gMSA + profil `prod` + sekret
LDAP w env) i jest zachowany jako droga powrotna (ADR-0005). Nie używać go pod `declared`:
profil `prod` aktywuje LDAP i usługa nie wstanie. Sekcje „gMSA", „mssql-jdbc_auth DLL"
i „Windows-ROOT" poniżej dotyczą wyłącznie wariantu A.

## Wariant declared — instalacja (wykonana; do odtworzenia serwera)

1. `WinSW.NET461.exe` (WinSW 2.x, `D:\instalki`) → `D:\portal\api\portal-api.exe`.
2. `deploy/winsw/portal-api.declared.xml` → `D:\portal\api\portal-api.xml`
   (sprawdzić ścieżkę JDK w `<env name="JAVA_HOME">` — usługa nie dziedziczy zmiennych sesji).
3. [SERWER #3] `& D:\portal\api\portal-api.exe install` → `Get-Service portal-api`
   (Stopped / Automatic). Zatrzymać ewentualną ręczną instancję Javy (port 8080).
4. `Start-Service portal-api` → `Invoke-RestMethod http://127.0.0.1:8080/actuator/health`
   → `UP`.
5. Testy: restart usługi, wylogowanie z RDP (usługa żyje), łagodne zamknięcie
   (`Stop-Service` → w `portal-api.out.log` linie `Commencing graceful shutdown`
   i `Graceful shutdown complete`), restart serwera (Faza 12).

WinSW 2.x: komendy BEZ myślników (`version`, nie `--version`). Zegary: aplikacja
czeka na żądania w toku 20 s (`spring.lifecycle.timeout-per-shutdown-phase`),
WinSW zabija po 30 s (`stoptimeout`) — kolejność musi zostać.

## Wariant A (gMSA, profil `prod`) — instalacja


1. Pobierz `WinSW-x64.exe` (GitHub: winsw/winsw, release 2.x/3.x) i przenieś offline na serwer.
2. Ułóż pliki:
   ```
   D:\portal\api\
     portal-api.exe        <- WinSW-x64.exe po zmianie nazwy
     portal-api.xml        <- wypełniony template (CHANGE_ME!)
     portal-api.jar
     mssql-jdbc_auth-<wersja>.x64.dll
   ```
3. ACL na pliku z sekretem (odczyt: administratorzy, SYSTEM i konto usługi):
   ```
   icacls D:\portal\api\portal-api.xml /inheritance:r ^
     /grant:r "*S-1-5-32-544:F" "*S-1-5-18:R" "DZI\gMSA-PortalApi$:R"
   (SID-y zamiast nazw: `*S-1-5-32-544` = Administratorzy, `*S-1-5-18` = SYSTEM —
   odporne na polską lokalizację Windows)
   ```
4. Instalacja i start:
   ```
   D:\portal\api\portal-api.exe install
   D:\portal\api\portal-api.exe start
   D:\portal\api\portal-api.exe status
   ```
5. Health check: `Invoke-RestMethod http://127.0.0.1:8080/actuator/health`

## Uwagi

- **gMSA**: przed instalacją usługi na hoście musi przejść
  `Test-ADServiceAccount gMSA-PortalApi` (po `Install-ADServiceAccount`).
  Login w `<serviceaccount>` kończy się `$`, hasła nie podaje się nigdzie.
- **mssql-jdbc_auth DLL**: wersja x64 zgodna z wersją sterownika `mssql-jdbc`
  (do pobrania z release'ów GitHub microsoft/mssql-jdbc). Bez niej
  `integratedSecurity=true` kończy się `no mssql-jdbc_auth in java.library.path`.
- **Windows-ROOT**: flaga `-Djavax.net.ssl.trustStoreType=Windows-ROOT` każe JVM ufać
  magazynowi certyfikatów Windows — cert LDAPS kontrolera domeny (AD CS) działa bez
  importu do cacerts.
- **Logi**: wrapper pisze `portal-api.wrapper.log` i `portal-api.out.log` w katalogu roboczym.
- **portal-worker**: analogiczna konfiguracja (osobny xml, id `portal-worker`,
  konto `DZI\gMSA-PortalWorker$`) — dojdzie przy Etapie 4.

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
