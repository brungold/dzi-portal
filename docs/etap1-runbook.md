# Etap 1 — runbook: stalowa nitka security na serwerze DZI-APP01V

## Cel i kryteria akceptacji

Przeglądarka na **innej stacji** → `https://portal.dzi.pl/api/whoami` i:

1. odpowiedź 200 **bez żadnego promptu o hasło** (czysty SSO),
2. `login` = Twój sAMAccountName,
3. `groups` zawiera Twoje grupy `DZI-Portal-*` rozwiązane z AD (w tym zagnieżdżone),
4. `klist` na stacji pokazuje bilet `HTTP/portal.dzi.pl` (dowód Kerberos, nie NTLM),
5. w `audit_log` wpis SUCCESS z Twoim loginem i **IP stacji** (nie serwera),
6. test anty-spoof z `verify-etap1.ps1` na zielono (nagłówek klienta nadpisany).

Do tego: `curl` bez nagłówka lokalnie → 401 i wpis DENIED w audycie.

## Wymagane wcześniej (tickety Etapu −1)

RDP odblokowane · rekord **A** `portal.dzi.pl → 10.0.22.150` · SPN
`setspn -S HTTP/portal.dzi.pl DZI-APP01V$` · certyfikat TLS z AD CS w LocalMachine\My ·
minimum jedna grupa `DZI-Portal-Admin` z Tobą w środku · konto **svc-portal-ldap** ·
GPO: portal.dzi.pl w strefie Intranet · firewall 443 ze stacji do VLAN 822.
**gMSA może poczekać do Fazy B** — Faza A działa bez nich.

## FAZA A — łańcuch dowieziony z konsoli (pod Twoim kontem)

Cel fazy: najpierw udowodnić łańcuch, dopiero potem go "usługowić".

**A1. Java.** MSI Temurin JDK 21 (offline). Sprawdź: `java -version`.

**A2. Baza.** SQL Server Express (jeśli brak). Potem w SSMS:
```sql
CREATE DATABASE portal;
-- tymczasowo, tylko na Fazę A (zdejmiesz w B3):
CREATE LOGIN [DZI\twoj.login] FROM WINDOWS;
USE portal; CREATE USER [DZI\twoj.login] FOR LOGIN [DZI\twoj.login];
ALTER ROLE db_owner ADD MEMBER [DZI\twoj.login];
```

**A3. Build i transfer.** Na stacji: `mvn -pl portal-api -am clean package`
→ `portal-api/target/portal-api-0.1.0-SNAPSHOT.jar` do `D:\portal\api\portal-api.jar`.

**A4. DLL integrated security.** `mssql-jdbc_auth-<wersja>.x64.dll` → `D:\portal\api\`.

**A5. LDAP.** Jeśli svc-portal-ldap już jest — użyj go. Jeśli ticket wisi: na czas smoke
testu możesz zbindować **własnym kontem** (każde uwierzytelnione konto czyta AD);
podmień na serwisowe zanim cokolwiek zostanie na stałe.

**A6. Start w konsoli** (cmd, katalog D:\portal\api):
```bat
set PORTAL_LDAP_USER_DN=CN=svc-portal-ldap,OU=Konta serwisowe,DC=dzi,DC=pl
set PORTAL_LDAP_PASSWORD=***
java -Dfile.encoding=UTF-8 -Djavax.net.ssl.trustStoreType=Windows-ROOT ^
     -Djava.library.path=D:\portal\api -jar portal-api.jar --spring.profiles.active=prod
```
W logu: migracja Flyway V1, start na 127.0.0.1:8080.

**A7. Smoke lokalny** (drugie okno):
```bat
curl -i http://127.0.0.1:8080/api/whoami                              & rem -> 401
curl -i -H "X-Auth-User: DZI\twoj.login" http://127.0.0.1:8080/api/whoami  & rem -> 200
```
To jest **pierwszy prawdziwy test resolvera LDAP** — w `groups` mają być Twoje grupy
DZI-Portal-*. Puste grupy = debuguj LDAP zanim dołożysz IIS (tabela na dole).

**A8. IIS.** `deploy/iis/setup-iis.ps1 -CertThumbprint <odcisk>` (PowerShell jako admin).

**A9. Weryfikacja na serwerze.** `deploy/iis/verify-etap1.ps1` — wszystkie punkty zielone.

**A10. TEST DECYDUJĄCY — z innej stacji.**
`klist purge`, przeglądarka → `https://portal.dzi.pl/api/whoami`, potem `klist`
(bilet HTTP/portal.dzi.pl). Test z serwera NIE liczy się jako zaliczenie —
loopback potrafi przejść na NTLM i zamaskować zepsuty Kerberos.

**A11. Audyt.** `SELECT TOP 20 * FROM audit_log ORDER BY id DESC` — wpis SUCCESS,
Twój login, IP **stacji**; wcześniejsze 401 jako DENIED.

## FAZA B — z konsoli do usługi

**B1. gMSA** (gdy ticket gotowy): na serwerze
`Install-ADServiceAccount gMSA-PortalApi` → `Test-ADServiceAccount gMSA-PortalApi` = True.

**B2. Granty produkcyjne.** `deploy/sql/prod-grants.sql` (loginy gMSA, role, DENY na audit_log).

**B3. Sprzątanie po Fazie A.** Zdejmij `db_owner` ze swojego konta.

**B4. Usługa.** `deploy/winsw/README-WinSW.md` (template → portal-api.xml, icacls, install, start).
Env z A6 przenoszą się do `<env>` w XML — konsola już ich nie potrzebuje.

**B5. Weryfikacja końcowa.** `verify-etap1.ps1` + test ze stacji + **restart serwera** —
po restarcie wszystko ma wstać samo (usługa Automatic/Delayed).

## Debugowanie — objaw → najczęstsza przyczyna

| Objaw | Sprawdź |
|---|---|
| Prompt o login/hasło na stacji | portal.dzi.pl poza strefą Intranet (GPO); przeglądarka nie wysyła Negotiate |
| Pętla 401.1 mimo poprawnego hasła | brak/duplikat SPN (`setspn -Q HTTP/portal.dzi.pl`); SPN na złym koncie vs kernel-mode |
| Działa, ale `klist` bez biletu HTTP/... | poszło NTLM-em: CNAME zamiast A, brak SPN, provider NTLM przed Negotiate |
| whoami → 500 | LDAP: zły URL/port 636, brak flagi Windows-ROOT (zaufanie do certu DC), złe DN/hasło konta bind — szczegóły w logu z correlationId |
| whoami → 200, `groups: []` | prefiks grup (`portal.ldap.group-prefix`) vs faktyczne nazwy; członkostwo; literówka w sAMAccountName grupy |
| 502.3 / 404 z IIS na /api | ARR proxy wyłączone; brak allowed server variables; backend nie działa (health) |
| `no mssql-jdbc_auth in java.library.path` | DLL x64 nie w `-Djava.library.path` / zła wersja względem sterownika |
| Login `DZI-APP01V$` zamiast usera w whoami | Anonymous auth włączone albo test robiony technicznym kanałem — sprawdź pkt 4 verify |
| Krzaki w polskich znakach w konsoli | `chcp 65001` + `-Dfile.encoding=UTF-8` (usługa loguje do pliku — tam OK) |

## Rollback

Konsola: Ctrl+C. Usługa: `Stop-Service portal-api`, poprzedni jar wraca przez
`portal-api.previous.jar` (patrz `deploy/deploy-api.ps1`). IIS można zostawić —
bez backendu zwraca 502 tylko na /api, statyka działa.

---

*Autor: Maciej Myśliwiec, 2026. *
