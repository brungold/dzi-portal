#Requires -RunAsAdministrator
# ======================================================================
# Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
# Autor: Maciej Myśliwiec, 2026.
# ======================================================================
<#
.SYNOPSIS
  Etap 1: weryfikacja stalowej nitki security na SERWERZE.
  Sprawdza po kolei kazde ogniwo: DNS -> SPN -> czas -> IIS -> backend -> pelny lancuch -> anty-spoof.

.NOTES
  Testy 8-9 wykonane z serwera bywaja mylace (loopback). Wynik decydujacy
  daje test z INNEJ stacji — instrukcja na koncu wydruku.
#>
[CmdletBinding()]
param(
    [string]$HostName   = 'portal.dzi.pl',
    [string]$ExpectedIp = '10.0.22.150',
    [string]$SiteName   = 'portal.dzi.pl',
    [string]$BackendUrl = 'http://127.0.0.1:8080'
)

$ErrorActionPreference = 'Continue'
Import-Module WebAdministration -ErrorAction SilentlyContinue
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}

$script:failures = 0
function OK($msg)   { Write-Host "[ OK ] $msg" -ForegroundColor Green }
function WARN($msg) { Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function FAIL($msg) { Write-Host "[FAIL] $msg" -ForegroundColor Red; $script:failures++ }

Write-Host "=== Weryfikacja Etapu 1: $HostName ===" -ForegroundColor Cyan

# --- 1. DNS: rekord A (nie CNAME) --------------------------------------------
try {
    $dns = Resolve-DnsName -Name $HostName -ErrorAction Stop
    if ($dns | Where-Object Type -eq 'CNAME') {
        FAIL "DNS: $HostName jest CNAME — Kerberos zbuduje SPN dla nazwy kanonicznej. Zamien na rekord A."
    } else {
        $a = $dns | Where-Object Type -eq 'A' | Select-Object -First 1
        if ($a.IPAddress -eq $ExpectedIp) { OK "DNS: rekord A -> $($a.IPAddress)" }
        else { WARN "DNS: rekord A -> $($a.IPAddress), oczekiwano $ExpectedIp" }
    }
} catch { FAIL "DNS: brak rozwiazania nazwy $HostName ($($_.Exception.Message))" }

# --- 2. SPN: istnieje, na koncie komputera, bez duplikatow -------------------
$spnOut = & setspn -Q "HTTP/$HostName" 2>&1 | Out-String
if ($spnOut -match 'Existing SPN found|Istniejaca nazwa SPN') {
    $accounts = ($spnOut -split "`r?`n" | Where-Object { $_ -match '^CN=' })
    if ($accounts.Count -gt 1) {
        FAIL "SPN: DUPLIKAT — HTTP/$HostName widnieje na $($accounts.Count) kontach. Kerberos przestaje dzialac po cichu. Usun nadmiarowe (setspn -D)."
    } else {
        $acct = ($accounts | Select-Object -First 1)
        if ($acct -match [regex]::Escape($env:COMPUTERNAME)) { OK "SPN: HTTP/$HostName na koncie komputera ($env:COMPUTERNAME$)" }
        else { WARN "SPN: HTTP/$HostName istnieje, ale na innym koncie: $acct — jesli pula IIS chodzi na koncie domyslnym, SPN powinien byc na koncie komputera." }
    }
} else {
    FAIL "SPN: brak HTTP/$HostName. Wykonaj: setspn -S HTTP/$HostName $env:COMPUTERNAME$"
}

# --- 3. Synchronizacja czasu (Kerberos toleruje ~5 min) -----------------------
$w32 = & w32tm /query /status 2>&1 | Out-String
if ($w32 -match 'Source:\s*(.+)') {
    $src = $Matches[1].Trim()
    if ($src -match 'Local CMOS|Free-running') { FAIL "Czas: zrodlo '$src' — serwer nie synchronizuje sie z domena." }
    else { OK "Czas: synchronizacja z '$src'" }
} else { WARN 'Czas: nie udalo sie odczytac w32tm /query /status' }

# --- 4. IIS: witryna + uwierzytelnianie --------------------------------------
$apphost = 'MACHINE/WEBROOT/APPHOST'
$site = Get-Website -Name $SiteName -ErrorAction SilentlyContinue
if (-not $site) { FAIL "IIS: brak witryny $SiteName — uruchom setup-iis.ps1" }
else {
    if ($site.State -eq 'Started') { OK "IIS: witryna $SiteName dziala" } else { FAIL "IIS: witryna $SiteName w stanie $($site.State)" }

    $anon = Get-WebConfigurationProperty -PSPath $apphost -Location $SiteName -Filter 'system.webServer/security/authentication/anonymousAuthentication' -Name 'enabled'
    if ($anon.Value) { FAIL 'IIS: Anonymous Authentication WLACZONE — LOGON_USER bedzie pusty.' } else { OK 'IIS: Anonymous wylaczone' }

    $win = Get-WebConfigurationProperty -PSPath $apphost -Location $SiteName -Filter 'system.webServer/security/authentication/windowsAuthentication' -Name 'enabled'
    if ($win.Value) { OK 'IIS: Windows Authentication wlaczone' } else { FAIL 'IIS: Windows Authentication WYLACZONE' }

    $providers = (Get-WebConfigurationProperty -PSPath $apphost -Location $SiteName -Filter 'system.webServer/security/authentication/windowsAuthentication/providers' -Name '.').Collection
    $first = ($providers | Select-Object -First 1).value
    if ($first -eq 'Negotiate') { OK "IIS: providers = $($providers.value -join ', ')" }
    else { FAIL "IIS: pierwszy provider to '$first' — Negotiate musi byc przed NTLM." }
}

# --- 5. Allowed server variables ----------------------------------------------
foreach ($var in 'HTTP_X_AUTH_USER', 'HTTP_X_FORWARDED_FOR') {
    $v = Get-WebConfigurationProperty -PSPath $apphost -Filter "system.webServer/rewrite/allowedServerVariables/add[@name='$var']" -Name 'name' -ErrorAction SilentlyContinue
    if ($v) { OK "Rewrite: allowed server variable $var" } else { FAIL "Rewrite: BRAK allowed server variable $var — regula nie ustawi naglowka." }
}

# --- 6. ARR proxy ---------------------------------------------------------------
$proxy = Get-WebConfigurationProperty -PSPath $apphost -Filter 'system.webServer/proxy' -Name 'enabled' -ErrorAction SilentlyContinue
if ($proxy -and $proxy.Value) { OK 'ARR: proxy wlaczone' } else { FAIL 'ARR: proxy WYLACZONE (Server Proxy Settings -> Enable proxy) albo ARR niezainstalowane.' }

# --- 7. Backend: Spring na loopbacku ------------------------------------------
try {
    $health = Invoke-RestMethod -Uri "$BackendUrl/actuator/health" -TimeoutSec 5 -UseBasicParsing
    if ($health.status -eq 'UP') { OK "Backend: $BackendUrl -> UP" } else { WARN "Backend: health = $($health.status)" }
} catch { FAIL "Backend: $BackendUrl nie odpowiada — uruchom portal-api (konsola/usluga). ($($_.Exception.Message))" }

# --- 8. Pelny lancuch z serwera (orientacyjnie) --------------------------------
try {
    $who = Invoke-RestMethod -Uri "https://$HostName/api/whoami" -UseDefaultCredentials -TimeoutSec 10 -UseBasicParsing
    OK "Lancuch: /api/whoami -> login='$($who.login)', grupy=$($who.groups.Count) [test z serwera — patrz uwaga nizej]"
    if ($who.groups.Count -eq 0) { WARN 'Lancuch: 0 grup — sprawdz LDAP (konto bind, prefiks grup, czlonkostwo).' }
} catch { FAIL "Lancuch: https://$HostName/api/whoami nie dziala z serwera. ($($_.Exception.Message))" }

# --- 9. Anty-spoof: naglowek od klienta musi zostac NADPISANY -------------------
try {
    $spoof = Invoke-RestMethod -Uri "https://$HostName/api/whoami" -UseDefaultCredentials -TimeoutSec 10 -UseBasicParsing `
        -Headers @{ 'X-Auth-User' = 'DZI\spoofed.user' }
    if ($spoof.login -eq 'spoofed.user') {
        FAIL 'ANTY-SPOOF: klient przemycil X-Auth-User!!! Regula musi ustawiac HTTP_X_AUTH_USER bezwarunkowo (allowed server variables + serverVariables w regule).'
    } else {
        OK "Anty-spoof: naglowek klienta nadpisany przez IIS (login='$($spoof.login)')"
    }
} catch { WARN "Anty-spoof: nie udalo sie wykonac testu ($($_.Exception.Message))" }

# --- Podsumowanie ---------------------------------------------------------------
Write-Host ''
if ($script:failures -eq 0) {
    Write-Host '=== WSZYSTKO OK na serwerze ===' -ForegroundColor Green
} else {
    Write-Host "=== Bledow: $script:failures ===" -ForegroundColor Red
}
Write-Host ''
Write-Host 'DECYDUJACY test wykonaj z INNEJ stacji (zalogowany uzytkownik domenowy):' -ForegroundColor Cyan
Write-Host "  1. klist purge; przegladarka: https://$HostName/api/whoami  (bez promptu o haslo!)"
Write-Host "  2. klist | findstr $HostName   -> bilet HTTP/$HostName potwierdza Kerberos (nie NTLM)"
Write-Host "  3. PowerShell: Invoke-RestMethod https://$HostName/api/whoami -UseDefaultCredentials"
