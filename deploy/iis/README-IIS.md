# IIS — konfiguracja witryny arimr-app.zszik.pl (stan faktyczny, zweryfikowany 2026-09-07)

> Witryna `portal` serwuje statyczny frontend z `D:\portal\frontend` i proxuje
> `/api/*` ORAZ `/apps/*` do aplikacji Spring na `127.0.0.1:8080` (URL Rewrite + ARR).
> **Windows Authentication JEST źródłem tożsamości**: moduł `PortalAuthUserHeader` v3.0
> wpisuje po stronie IIS uwierzytelniony login (`X-Auth-User`) i departament z pierwszego
> OU w AD (`X-Auth-Dept`) — ADR-0006, ADR-0008. Ten dokument opisuje stan faktyczny;
> historyczny projekt z Kerberosem/SPN/HTTPS (Etap 1) — patrz runbooki w docs/.

## Stan witryny

- Serwer: DZI-APP01V (10.0.22.150), witryna `portal` (id 2).
- Binding: `http / 80 / arimr-app.zszik.pl` (rekord A w DNS). TLS — w backlogu.
- Brak SPN dla aliasu → uwierzytelnianie realnie idzie po **NTLM**
  (Negotiate spada na NTLM); to świadomy, działający stan.
- **Authentication**: Anonymous **Disabled**, Windows Authentication **Enabled**,
  providers: Negotiate, NTLM.
- **Kernel-mode authentication: WŁĄCZONE (useKernelMode="true") — nie wyłączać.**
  Próba wyłączenia (2026-08-10) zabiła NTLM dla wszystkich klientów błędem
  0x80090305 "pakiet zabezpieczeń nie został znaleziony" w trybie użytkownika.
- Pula `portal`: .NET CLR v4.0.30319, Integrated, ApplicationPoolIdentity
  (wymóg modułu zarządzanego; funkcja systemowa ASP.NET 4.8 doinstalowana).
- Moduł `PortalAuthUserHeader` (`D:\portal\frontend\bin\PortalAuthUserModule.dll`,
  źródło `deploy/iis/AuthUserHeaderModule.cs`): rejestracja w sekcji `<modules>`
  web.config witryny — **bez preCondition**. Weryfikacja rejestracji:
  `appcmd list config "portal" -section:system.webServer/modules`.
- Reguła rewrite `portal-api-proxy`: proxy `/api/*` + `HTTP_X_FORWARDED_FOR`.
  `HTTP_X_AUTH_USER` z `{LOGON_USER}` — celowo NIE: reguły działają przed
  uwierzytelnieniem, wartość byłaby zawsze pusta (diagnoza 2026-08-06).
- Reguła rewrite `portal-apps-proxy`: proxy `/apps/*` (strażnik, ADR-0007) — bez
  `serverVariables`; ARR dokłada `X-Forwarded-For` sam (audyt `APP_OPEN` ma adres
  stacji — potwierdzone 2026-09-08).
- Pliki modułów kafelków NIE leżą w witrynie (`D:\portal\apps`, poza `D:\portal\frontend`).
- Kompilacja modułu v3.0 wymaga `/reference:System.DirectoryServices.dll`
  (`INSTRUKCJA-MODUL-WINDOWS-AUTH.md`).

## Stacje użytkowników (SSO bez okna hasła)

`http://arimr-app.zszik.pl` w strefie **Intranet lokalny** (na razie ręcznie:
inetcpl.cpl → Zabezpieczenia → Intranet lokalny → Witryny → Zaawansowane;
docelowo GPO Site to Zone Assignment). Bez wpisu przeglądarka pyta o login
i hasło domenowe — logowanie działa, ale nie jest bezszelestne.

## Weryfikacja end-to-end (ze stacji, NIE z serwera)

- `curl.exe --ntlm -u : "http://arimr-app.zszik.pl/api/whoami" --noproxy "*" -i`
  → 200 i login zalogowanego użytkownika.
- Anty-podszycie: to samo z `-H "X-Auth-User: abcde"` → nadal prawdziwy login.
- Testy z samego serwera po aliasie NIE przechodzą (loopback check, KB896861)
  — to zabezpieczenie Windows, nie błąd konfiguracji. Obejście (wpis rejestru
  BackConnectionHostNames + restart) zaplanowane przy Fazie 12.
