# Windows Auth przez moduł IIS — instrukcja wdrożenia (paczka B)

Cel: IIS uwierzytelnia użytkownika (NTLM/Kerberos), a malutki moduł wpisuje jego
login do nagłówka `X-Auth-User` PO etapie uwierzytelnienia — tam, gdzie reguła
URL Rewrite nie sięga (reguły działają w BeginRequest, przed uwierzytelnieniem,
dlatego `{LOGON_USER}` był w nich zawsze pusty; diagnoza 2026-08-06).

Moduł to jeden plik DLL ładowany przez IIS jak URL Rewrite — żaden osobny
program, żadna usługa, zero instalatorów z internetu.

## Założenia startowe

- Witryna: `arimr-app.zszik.pl`, katalog `D:\portal\frontend`, pula `portal`.
- Windows Authentication: **Enabled**, Anonymous: **Disabled** (stan z 2026-08-06).
- Paczka A wdrożona (zalecane: bez niej pusty nagłówek przy anonimowym żądaniu
  wywala wyjątek zamiast 401; z modułem i wyłączonym Anonymous to scenariusz
  teoretyczny, ale porządek zobowiązuje).
- Konsola #3 (administrator) do kroków 1–4.

## Krok 1 — funkcja systemowa ASP.NET 4.8 (raz na serwer)

Server Manager → Add Roles and Features → Server Roles →
Web Server (IIS) → Web Server → **Application Development** → zaznacz
**ASP.NET 4.8** (kreator sam dobierze .NET Extensibility 4.8 oraz ISAPI —
zgódź się). Ten sam kreator, którym doszła dziś Windows Authentication.

Weryfikacja (PowerShell):

    Get-WindowsFeature Web-Asp-Net45

Install State ma być `Installed` (nazwa funkcji została historycznie
`Web-Asp-Net45`, choć obejmuje 4.8).

## Krok 2 — pula `portal` na potok zarządzany

IIS Manager → Application Pools → `portal` → Basic Settings:

- .NET CLR version: **v4.0.30319** (zamiast „No Managed Code"),
- Managed pipeline mode: **Integrated**.

Alternatywnie (konsola #3):

    %windir%\system32\inetsrv\appcmd set apppool "portal" /managedRuntimeVersion:v4.0 /managedPipelineMode:Integrated

## Krok 3 — kompilacja modułu (kompilator już jest w Windows)

1. Skopiuj `AuthUserHeaderModule.cs` do `D:\portal\deploy\iis\`.
2. Utwórz katalog `D:\portal\frontend\bin` (jeśli nie istnieje).
3. Skompiluj (konsola #3, jedna linia):

       C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe /nologo /codepage:65001 /target:library /reference:System.Web.dll /out:D:\portal\frontend\bin\PortalAuthUserModule.dll D:\portal\deploy\iis\AuthUserHeaderModule.cs

   `/codepage:65001` = plik jest w UTF-8 (polskie znaki w komentarzach).

Weryfikacja: `dir D:\portal\frontend\bin` pokazuje `PortalAuthUserModule.dll`.

## Krok 4 — web.config (D:\portal\frontend\web.config)

Dwie zmiany (wzorzec całego pliku: `deploy/iis/web.config` w repo; na serwerze
edytuj punktowo, nie nadpisuj — Twój plik może mieć lokalne dodatki):

1. W `<system.webServer>` dołóż sekcję modułów (jeśli `<modules>` już istnieje —
   tylko wiersz `<add>`):

       <modules>
           <add name="PortalAuthUserHeader"
                type="PortalAuthUserModule.AuthUserHeaderModule, PortalAuthUserModule" />
       </modules>

   CELOWO bez `preCondition="managedHandler"` — z nim moduł ominąłby żądania
   proxowane przez ARR (klasyczna pułapka).

2. W regule `portal-api-proxy` USUŃ (albo zostaw zakomentowaną) linię:

       <set name="HTTP_X_AUTH_USER" value="{LOGON_USER}" />

   Jest martwa architektonicznie i tylko myli. `HTTP_X_FORWARDED_FOR` zostaje!
   Wpis `HTTP_X_AUTH_USER` na liście Allowed Server Variables może zostać —
   moduł z niego nie korzysta, obecność nie szkodzi.

Gdyby IIS zgłosił „section locked" przy zapisie:

    %windir%\system32\inetsrv\appcmd unlock config -section:system.webServer/modules

## Krok 5 — frontend

Podmień `D:\portal\frontend\js\declared-identity.js` plikiem z paczki
(sonda whoami zawsze pierwsza; przy 200 stara deklaracja z localStorage
czyści się sama). W przeglądarce odśwież z pominięciem cache (Ctrl+F5).

## Krok 6 — testy (konsola #2, bez uprawnień administratora)

1. SSO bieżącym kontem (z sesji RDP jesteś zalogowany jako Ty):

       curl.exe --ntlm -u : "http://arimr-app.zszik.pl/api/whoami" --noproxy "*" -i

   Oczekiwane: `200`, `{"login":"maciej.mysliwiec","groups":["maciej.mysliwiec","wszyscy"]}`.

2. Próba podszycia się nagłówkiem — moduł ma nadpisać:

       curl.exe --ntlm -u : -H "X-Auth-User: prezes" -H "X-Auth-Dept: kadry" "http://arimr-app.zszik.pl/api/whoami" --noproxy "*" -i

   Oczekiwane: nadal Twój login, bez śladu „prezesa" i „kadr". To jest test,
   który warto pokazać przełożonemu — audyt przestał być deklaracją.

3. Bez uwierzytelnienia w ogóle:

       curl.exe "http://arimr-app.zszik.pl/api/whoami" --noproxy "*" -i

   Oczekiwane: `401` od IIS (challenge NTLM/Negotiate) — żądanie nie dociera
   do aplikacji.

4. Przeglądarka na stacji: F5 → portal **bez okna deklaracji**, login w hero.
   W bazie (konsola #2):

       sqlcmd -S localhost -d portal -E -Q "SELECT TOP 5 ts_pl, username, client_ip, path, status, http_status FROM v_audit_log_pl ORDER BY id DESC"

   Oczekiwane: wpisy z prawdziwym loginem i adresem IP stacji, czas polski.

5. Testerzy z wcześniejszą deklaracją: nic nie muszą robić — nowy frontend
   sam czyści zapis. Ręczna alternatywa: w konsoli F12 `PortalIdentity.clear()`.

## Rollback (w minutę)

- Wyłączenie samego modułu: usuń wiersz `<add name="PortalAuthUserHeader" ...>`
  z web.config. Portal dalej działa: whoami zwróci 401, frontend pokaże okno
  deklaracji (tryb declared „przez" Windows Auth).
- Pełny powrót do stanu sprzed Windows Auth: dodatkowo Authentication →
  Anonymous **Enable**, Windows Authentication **Disable**.
- Pula może zostać na v4.0 (nieszkodliwa), DLL może zostać w bin (martwy bez
  wpisu w web.config).

## Najczęstsze potknięcia

- `500.19` po kroku 4 → literówka w `type` (wielkość liter się liczy) albo
  sekcja `<modules>` zablokowana (patrz appcmd unlock wyżej).
- Moduł „nie działa" dla /api, statyka OK → wpis ma `preCondition="managedHandler"`
  — usuń ten atrybut.
- `csc.exe` nie znaleziony → użyj pełnej ścieżki z kroku 3 (Framework64).
- Test 1 zwraca 401 mimo poprawnej konfiguracji → sprawdź, czy pula po zmianie
  wersji CLR wystartowała (IIS Manager → Application Pools → Status: Started).
