# Etap 6 — runbook: odświeżanie, hardening, deploy z rollbackiem

## A. Kod (commity 27–28) i weryfikacja lokalna

1. `mvn clean verify` — nowe testy: ETag/304 w slice zbiorów, pominięcie 304 w audycie.
2. Demo odświeżania: dwie karty `dataset.html?code=licencje`; edytuj komórkę w pierwszej →
   druga w ≤10 s pokaże zmianę i dopisze „odświeżono HH:MM:SS". W DevTools widać serię 304
   i pojedyncze 200. W `audit_log` — ZERO wpisów z pollingu, jest wpis edycji.

## B. Serwer — kolejność

1. **Worker jako usługa**: `deploy/winsw/portal-worker.xml.template` → `D:\portal\worker\`
   (bez sekretów — integrated security), install/start jak w README-WinSW.
2. **NTFS na skryptach** (granica RCE):
   ```
   icacls D:\portal\scripts /inheritance:r ^
     /grant:r "*S-1-5-32-544:(OI)(CI)F" "*S-1-5-18:(OI)(CI)F" ^
              "DZI\gMSA-PortalWorker$:(OI)(CI)RX"
   (SID-y: `*S-1-5-32-544` = Administratorzy, `*S-1-5-18` = SYSTEM — polska lokalizacja)
   ```
3. **Retencja**: `deploy/register-audit-retention.ps1 -RunAsUser 'DZI\svc-portal-maint' ...`
   + jednorazowe GRANT DELETE dla tego konta (nagłówek `audit-retention.sql`).
4. **Pierwszy pełny deploy**: `deploy\deploy.ps1 -ApiJar ... -WorkerJar ... -FrontendSource ...`
   — skrypt sam: stop (worker→api) → backup → podmiana → robocopy frontendu
   (web.config chroniony) → start api (migracje) → smoke whoami → start workera.

## C. TEST ROLLBACKU (ćwiczenie obowiązkowe, ~5 minut)

1. Zanotuj bieżący build: `Invoke-RestMethod http://127.0.0.1:8080/actuator/info`.
2. Wykonaj deploy nowego jara → sprawdź, że build się ZMIENIŁ.
3. `deploy\deploy.ps1 -RollbackOnly` → build wrócił do zanotowanego, whoami działa,
   worker Running, kafelek demo wykonuje zadanie.
4. Deploy ponownie właściwej wersji.
Automatyczny rollback przy porażce smoke'a masz za darmo — ale przećwiczony ręczny
rollback to różnica między „5 minut" a „godzina paniki" przy realnej awarii.

## D. Przegląd hardeningowy

`deploy\verify-hardening.ps1` — wszystko zielone, w szczególności:
konta usług = właściwe gMSA · gMSA poza Administrators · 8080 tylko na 127.0.0.1 ·
worker BEZ zapisu do scripts · ACL na portal-api.xml · DENY na audit_log · zadanie retencji.
Na koniec regresja Etapu 1: `deploy\iis\verify-etap1.ps1` (łańcuch Kerberos + anty-spoof).

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
