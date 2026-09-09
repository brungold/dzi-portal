# Reguła URL Rewrite: /apps/* → aplikacja (ADR-0007) — DOKUMENT HISTORYCZNY

> **Stan od 2026-09-08:** reguła `portal-apps-proxy` jest scalona do
> `deploy/iis/web.config` (plik = stan serwera). Ten opis zostaje jako zapis
> procedury wdrożenia z 2026-08-13; przy odtwarzaniu serwera używaj `web.config`,
> nie tego dokumentu.

Edycja `D:\portal\frontend\web.config` — **Notatnik jako administrator** (lekcja
z sekcji <modules>: bez elevacji zapis idzie po cichu gdzie indziej).

W `<system.webServer><rewrite><rules>` istnieje reguła proxy dla `/api/*`.
BEZPOŚREDNIO POD NIĄ dodać bliźniaczą:

```xml
<rule name="portal-apps-proxy" stopProcessing="true">
  <match url="^apps(/.*)?$" />
  <action type="Rewrite" url="http://127.0.0.1:8080/apps{R:1}" />
</rule>
```

Wzorzec `^apps(/.*)?$` łapie też goły `/apps` (bez ukośnika) — aplikacja odpowiada
wtedy przekierowaniem na `/apps/<moduł>/`, więc IIS nie może go zatrzymać dla siebie.

## Obowiązkowa weryfikacja po zapisie

[SERWER #3]
```powershell
& "$env:windir\system32\inetsrv\appcmd.exe" list config "portal" -section:system.webServer/rewrite/rules
```
W wydruku muszą być OBIE reguły: api i apps. Brak apps = plik nie zapisał się tam,
gdzie myślisz (elevacja!).

## Rollback

Usunięcie bloku `<rule name="portal-apps-proxy">...</rule>` + ta sama weryfikacja.
IIS wraca do serwowania /apps ze statyki — o ile pliki fizycznie tam są.
