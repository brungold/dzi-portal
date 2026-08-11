# Windows Auth przez moduł IIS — instrukcja (zaktualizowana po wdrożeniu 2026-08-11)

Cel: IIS uwierzytelnia użytkownika (NTLM), a moduł `PortalAuthUserHeader` wpisuje
jego login do nagłówka `X-Auth-User` PO etapie uwierzytelnienia — tam, gdzie
reguła URL Rewrite nie sięga. Moduł to jeden plik DLL ładowany przez IIS jak
URL Rewrite — żaden osobny program, zero instalatorów z internetu.

**Stan: wdrożone i potwierdzone produkcyjnie 2026-08-11** (login z domeny
w audycie, anty-podszycie odbite, portal bez okna deklaracji). Instrukcja
zachowana na wypadek odtworzenia serwera; wzbogacona o lekcje z wdrożenia.

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

       C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe /nologo /codepage:65001 /target:library /reference:System.Web.dll /out:D:\portal\frontend\bin\PortalAuthUserModule.dll D:\portal\deploy\iis\AuthUserHeaderModule.cs

   Sukces = brak komunikatu. „File in use" → `appcmd recycle apppool "portal"`
   i ponów. Weryfikacja: `dir D:\portal\frontend\bin` (świeża data DLL).
4. **web.config** (`D:\portal\frontend\web.config`, wzorzec: `deploy/iis/web.config`):
   sekcja `<modules>` z wpisem modułu (bez `preCondition`!) jako dziecko
   `<system.webServer>`; linia `HTTP_X_AUTH_USER` w regule rewrite zakomentowana;
   `HTTP_X_FORWARDED_FOR` zostaje. **Po zapisie: weryfikacja z lekcji nr 2.**
5. **Frontend**: `frontend/js/declared-identity.js` w wersji z sondą whoami
   (repo). W przeglądarce Ctrl+F5.
6. **Testy — wyłącznie ze stacji roboczej** (loopback check blokuje testy
   z serwera po aliasie; KB896861):

       curl.exe --ntlm -u : "http://arimr-app.zszik.pl/api/whoami" --noproxy "*" -i

   → 200 i login testującego. Anty-podszycie: to samo z
   `-H "X-Auth-User: abcde"` → nadal prawdziwy login. Przeglądarka → portal
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
