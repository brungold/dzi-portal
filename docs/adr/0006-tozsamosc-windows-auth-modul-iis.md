# ADR-0006: Tożsamość z Windows Authentication przez moduł IIS

Status: zaakceptowany · Data: 2026-08-11 · Zastępuje model deklaracji tożsamości z ADR-0003/0005

## Kontekst

Model deklarowany (ADR-0003/0005) świadomie akceptował lukę: login w audycie był
deklaracją klienta, nie faktem. Przełożony zapytał o Windows Authentication,
a diagnoza z 2026-08-06 wykazała, że pierwotny pomysł transportu tożsamości —
reguła URL Rewrite kopiująca `{LOGON_USER}` do nagłówka — jest architektonicznie
martwy: reguły rewrite działają w etapie BeginRequest potoku IIS, PRZED
uwierzytelnieniem, więc wartość jest zawsze pusta. Ubocznie ujawniło to trzy
defekty aplikacji (paczka A: puste nagłówki, uczciwość audytu przy wyjątkach,
UTC w ts_utc).

## Decyzja

Tożsamość ustala IIS (Windows Authentication, w praktyce NTLM — alias bez SPN),
a do aplikacji przenosi ją **własny moduł zarządzany C#** `PortalAuthUserHeader`
(`deploy/iis/AuthUserHeaderModule.cs`), zaczepiony za etapem uwierzytelnienia:

- wpisuje uwierzytelniony login do `X-Auth-User`, NADPISUJĄC wartość od klienta
  (anty-podszycie — zweryfikowane testem: `X-Auth-User: abcde` → login prawdziwy),
- USUWA `X-Auth-Dept` od klienta — cała tożsamość pochodzi z IIS,
- przy braku uwierzytelnienia zostawia pusty nagłówek → aplikacja odpowiada 401.

Aplikacja Java pozostaje bez zmian (profil `declared` konsumuje ten sam nagłówek
z zaufanego loopbacku). Frontend: sonda `/api/whoami` zawsze pierwsza; 200 → tryb
przezroczysty bez okna deklaracji, 401 → okno (dev/awaryjnie). Uprawnienia
żądania = {login, wszyscy}; departament wróci w module v2 (odczyt pierwszego OU
z distinguishedName w AD — decyzja z 2026-08-10, `whoami /fqdn` potwierdził
`OU=DZI,OU=Biuro,OU=Centrala`).

## Ustalenia twarde z wdrożenia (2026-08-10/11)

1. **Kernel-mode authentication musi zostać WŁĄCZONE** (`useKernelMode="true"`).
   Wyłączenie zabija NTLM dla wszystkich klientów (0x80090305 w trybie
   użytkownika na tym utwardzonym serwerze). Rekomendacja wyłączenia z 2026-08-10
   była błędna i została wycofana.
2. Moduł działa tylko z wpisem w `<modules>` web.config witryny (bez
   preCondition). Brak wpisu = moduł nieładowany, objaw: pusty login mimo
   udanego uwierzytelnienia. Po każdej edycji web.config obowiązkowa
   weryfikacja: `appcmd list config "portal" -section:system.webServer/modules`.
3. Testy rozstrzygające wyłącznie ze stacji roboczej — loopback check blokuje
   NTLM serwer→serwer po aliasie (KB896861).
4. Wymagania infrastrukturalne: funkcja ASP.NET 4.8, pula .NET v4.0 Integrated.

## Konsekwencje

- Audyt rejestruje fakt: uwierzytelniony login + IP stacji (X-Forwarded-For).
- Okno deklaracji znika; stacjom potrzebny wpis strefy Intranet lokalny
  (ręcznie u testerów, docelowo GPO).
- Tabela `user_departments` i deklaracja departamentu — martwe; usunięte przy
  konsolidacji migracji (paczka porządkowa).
- ADR-0003 i ADR-0005 pozostają jako zapis kompensacji trybu dev/awaryjnego,
  ale model produkcyjny opisuje niniejszy ADR.

## Warianty odrzucone

- `{LOGON_USER}` w regule rewrite — martwe (kolejność potoku IIS).
- Wyłączenie kernel-mode, by moduł czytał tożsamość łatwiej — zabija NTLM (pkt 1).
- HttpPlatformHandler z forwardWindowsAuthToken — zmiana modelu hostingu
  i zależność waffle-jna w aplikacji; nieuzasadnione przy działającym module.
