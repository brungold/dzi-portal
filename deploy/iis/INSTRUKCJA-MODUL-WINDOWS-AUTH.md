# Windows Auth przez moduł IIS — instrukcja (moduł v3.0, stan 2026-09-08)

Cel: IIS uwierzytelnia użytkownika (NTLM), a moduł `PortalAuthUserHeader` wpisuje
PO etapie uwierzytelnienia — tam, gdzie reguła URL Rewrite nie sięga — dwa nagłówki:
`X-Auth-User` (login z domeny) i `X-Auth-Dept` (departament z AD, od v3.0).
Moduł to jeden plik DLL ładowany przez IIS jak URL Rewrite — żaden osobny program,
zero instalatorów z internetu.

**Stan: v2.0 wdrożone 2026-08-11 (login), v3.0 wdrożone 2026-08-12 (departament);
źródło `deploy/iis/AuthUserHeaderModule.cs` = plik na serwerze (zweryfikowane
2026-09-07).** Instrukcja zachowana na wypadek odtworzenia serwera.

## Departament (v3.0, ADR-0008)

- Źródło: **pierwsze `OU=` w `distinguishedName`** użytkownika
  (`CN=...,OU=DZI,OU=Biuro,OU=Centrala,DC=zszik,DC=pl` → `dzi`), małymi literami —
  spójnie z wartościami w `tile_permissions.ad_group`. NIE `extensionattribute12`
  (ADR-0005 opisywał deklarację; produkcja bierze OU).
- Zapytanie `DirectorySearcher` po `sAMAccountName` tożsamością puli aplikacji
  (`ApplicationPoolIdentity` = konto maszyny w domenie; odczyt DN nie wymaga
  żadnego konta ani hasła). Timeout 3 s.
- Cache w pamięci procesu w3wp per login: sukces 15 min, porażka 60 s.
  Recykling puli = pusty cache.
- **Tryb awaryjny jest niemy:** AD nie odpowiada → nagłówek departamentu nie powstaje,
  kafelki nadane na departament (`dzi`) znikają, login i kafelki imienne działają.
  Moduł nie ma loggera, aplikacja nie odnotowuje braku nagłówka. Przy zgłoszeniu
  „nie widzę kafelka, wczoraj widziałem" — to pierwszy podejrzany; sprawdzenie:
  `curl.exe --ntlm -u : http://arimr-app.zszik.pl/api/whoami --noproxy "*"` ze stacji
  (pole `groups` bez skrótu departamentu = moduł nie dostał DN).
- Anty-spoof obu nagłówków: `X-Auth-User` nadpisywany, `X-Auth-Dept` od klienta
  usuwany na `AuthenticateRequest`, wartości wstawiane na `PostAuthenticateRequest`.

## Trzy lekcje, które kosztowały dwa dni — czytaj przed czymkolwiek

1. **Kernel-mode authentication zostaje WŁĄCZONE.** (Windows Authentication →
   Advanced Settings → „Enable Kernel-mode authentication" = zaznaczone;
   Extended Protection = Off). Wyłączenie zabija NTLM dla wszystkich klientów
   (0x8009030c / 0xc000006d na bramce, w logu IIS win32=2148074245 czyli
   0x80090305). Moduł radzi sobie z trybem jądra sam.
2. **Po każdej edycji web.config zweryfikuj, że IIS widzi moduł:**
   `& "$env:windir\system32\inetsrv\appcmd.exe" list config "portal" -section:system.webServer/modules | Select-String "PortalAuthUserHeader"`
   Brak wyniku = wpis nie działa (zły plik, zła lokalizacja, niezapisane) —
   moduł NIE jest ładowany i login będzie pusty, choć wszystko inne wygląda
   poprawnie. Ta jedna komenda wykryłaby prawdziwą przyczynę od razu.
3. **Notatnik przy zapisie w katalogu witryny uruchamiaj jako administrator.**
   Bez tego zapis potrafi pójść po cichu do innego pliku/katalogu („Zapisz
   jako") — i edycja nigdy nie trafia na serwer.

## Założenia startowe

- Witryna `portal` (id 2), katalog `D:\portal\frontend`, pula `portal`,
  binding `http/*:80:arimr-app.zszik.pl`.
- Windows Authentication: Enabled, Anonymous: Disabled, kernel-mode: ON.
- Paczka A wdrożona (aplikacja traktuje pusty nagłówek jak brak tożsamości).
- Konsola #3 (administrator) do kroków 1–4.

## Kroki

1. **Funkcja ASP.NET 4.8** (raz na serwer): Server Manager → Add Roles and
   Features → Web Server (IIS) → Application Development → ASP.NET 4.8
   (kreator dobierze zależności). Weryfikacja: `Get-WindowsFeature Web-Asp-Net45`
   → Installed.
2. **Pula `portal`** → Basic Settings: .NET CLR v4.0.30319, Integrated.
   Po zmianie sprawdź, czy pula ma status Started.
3. **Kompilacja modułu** (kompilator jest w Windows; konsola #3, bo zapis idzie
   do katalogu witryny):

       C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe /nologo /codepage:65001 /target:library /reference:System.Web.dll /reference:System.DirectoryServices.dll /out:D:\portal\frontend\bin\PortalAuthUserModule.dll <ścieżka>\AuthUserHeaderModule.cs

   **Od v3.0 obowiązkowe `/reference:System.DirectoryServices.dll`** — bez niego
   kompilacja kończy się błędem CS0234 (`DirectoryServices` nie istnieje w `System`).
   `<ścieżka>` = kopia repo na serwerze (np. `D:\portal\dzi-portal-<data>\deploy\iis`).
   Sukces = brak komunikatu. „File in use" → `appcmd recycle apppool "portal"`
   i ponów. Weryfikacja: `dir D:\portal\frontend\bin` (świeża data DLL).
4. **web.config** (`D:\portal\frontend\web.config` = `deploy/iis/web.config` w repo,
   stan serwera): sekcja `<modules>` z wpisem modułu (bez `preCondition`!) jako
   dziecko `<system.webServer>`; reguły `portal-api-proxy` (z `HTTP_X_FORWARDED_FOR`)
   i `portal-apps-proxy` (strażnik, ADR-0007). **Po zapisie: weryfikacja z lekcji
   nr 2** oraz `appcmd list config "portal" -section:system.webServer/rewrite/rules`.
5. **Frontend**: `frontend/js/declared-identity.js` w wersji z sondą whoami
   (repo). W przeglądarce Ctrl+F5.
6. **Testy — wyłącznie ze stacji roboczej** (loopback check blokuje testy
   z serwera po aliasie; KB896861):

       curl.exe --ntlm -u : "http://arimr-app.zszik.pl/api/whoami" --noproxy "*" -i

   → 200, login testującego i w `groups` skrót departamentu (np. `dzi`).
   Anty-podszycie: to samo z `-H "X-Auth-User: abcde" -H "X-Auth-Dept: dag"`
   → nadal prawdziwy login i prawdziwy departament. Przeglądarka → portal
   bez okna deklaracji (systemowe okno hasła = brak wpisu strefy Intranet
   lokalny na stacji — inetcpl.cpl, docelowo GPO). Audyt:
   `SELECT TOP 10 * FROM v_audit_log_pl ORDER BY id DESC` → wpisy SUCCESS
   z loginem i IP stacji.

## Rollback

Usunięcie wpisu `<add name="PortalAuthUserHeader" .../>` z web.config wyłącza
moduł (whoami → 401, frontend pokaże okno deklaracji — tryb declared przez
Windows Auth). Pełny powrót: dodatkowo Anonymous Enable + Windows Auth Disable.
Pula może zostać na v4.0; DLL w bin jest martwa bez wpisu.

## Najczęstsze potknięcia

- Moduł „nie działa", login pusty, zero śladów → wpis w `<modules>` nie
  istnieje w scalonej konfiguracji (lekcja nr 2) albo plik edytowany bez
  uprawnień (lekcja nr 3).
- `500.19` → literówka w `type` (wielkość liter!) albo sekcja zablokowana:
  `appcmd unlock config -section:system.webServer/modules`.
- Moduł omija /api, statyka OK → wpis ma `preCondition="managedHandler"` — usuń atrybut.
- Testy z serwera zwracają 401.1 HTML → loopback check, nie błąd konfiguracji.
- Login jest, departamentu brak (`groups` bez skrótu) → AD nie odpowiedziało w 3 s
  albo DN bez `OU=`; po 60 s moduł próbuje ponownie. Recykling puli czyści cache.
- `CS0234 ... DirectoryServices` przy kompilacji → brak `/reference:System.DirectoryServices.dll`.
