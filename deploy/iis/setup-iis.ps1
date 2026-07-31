#Requires -RunAsAdministrator
# ======================================================================
# Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
# Autor: Maciej Myśliwiec, 2026.
# ======================================================================
<#
.SYNOPSIS
  Etap 1: konfiguracja IIS dla portal.dzi.pl — Windows Auth (Negotiate przed NTLM),
  ARR proxy, allowed server variables, witryna 443 + web.config z regula proxy do Springa.

.NOTES
  Skrypt jest idempotentny — mozna uruchamiac wielokrotnie.
  Wymaga wczesniej: URL Rewrite + ARR (instalacja z MSI offline) oraz certyfikatu
  z AD CS w magazynie LocalMachine\My.

.EXAMPLE
  # thumbprint bez spacji; liste certow pokazuje: Get-ChildItem Cert:\LocalMachine\My
  .\setup-iis.ps1 -CertThumbprint 'A1B2C3...'
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$CertThumbprint,
    [string]$SiteName     = 'portal.dzi.pl',
    [string]$HostName     = 'portal.dzi.pl',
    [string]$PhysicalPath = 'D:\portal\www',
    [string]$AppPoolName  = 'PortalPool'
)

$ErrorActionPreference = 'Stop'
Import-Module WebAdministration
$apphost = 'MACHINE/WEBROOT/APPHOST'

function Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }

# --- 0. Prerekwizyty: URL Rewrite + ARR --------------------------------------
Step 'Sprawdzam URL Rewrite i ARR'
if (-not (Test-Path "$env:SystemRoot\System32\inetsrv\rewrite.dll")) {
    throw 'Brak modulu URL Rewrite. Zainstaluj MSI (rewrite_amd64) przeniesione offline i uruchom skrypt ponownie.'
}
if (-not (Test-Path "$env:SystemRoot\System32\inetsrv\requestRouter.dll")) {
    throw 'Brak modulu ARR (Application Request Routing). Zainstaluj MSI (requestRouter_amd64) i uruchom ponownie.'
}

# --- 1. ARR: wlacz tryb proxy (poziom serwera) -------------------------------
Step 'Wlaczam ARR proxy'
Set-WebConfigurationProperty -PSPath $apphost -Filter 'system.webServer/proxy' -Name 'enabled' -Value 'True'

# --- 2. Allowed Server Variables (poziom serwera; bez tego regula nie zadziala)
Step 'Dodaje allowed server variables'
foreach ($var in 'HTTP_X_AUTH_USER', 'HTTP_X_FORWARDED_FOR') {
    $exists = Get-WebConfigurationProperty -PSPath $apphost `
        -Filter "system.webServer/rewrite/allowedServerVariables/add[@name='$var']" `
        -Name 'name' -ErrorAction SilentlyContinue
    if (-not $exists) {
        Add-WebConfigurationProperty -PSPath $apphost `
            -Filter 'system.webServer/rewrite/allowedServerVariables' -Name '.' -Value @{ name = $var }
        Write-Host "    dodano: $var"
    } else {
        Write-Host "    juz jest: $var"
    }
}

# --- 3. Pula aplikacji (bez .NET: statyka + proxy) ---------------------------
Step "Pula aplikacji: $AppPoolName"
if (-not (Test-Path "IIS:\AppPools\$AppPoolName")) { New-WebAppPool -Name $AppPoolName | Out-Null }
Set-ItemProperty "IIS:\AppPools\$AppPoolName" -Name managedRuntimeVersion -Value ''
# Pula na koncie domyslnym (ApplicationPoolIdentity) + SPN na koncie KOMPUTERA
# => kernel-mode authentication zostaje wlaczony (default) i Kerberos dziala.

# --- 4. Katalog + witryna + binding https ------------------------------------
Step "Witryna: $SiteName"
New-Item -ItemType Directory -Force -Path $PhysicalPath | Out-Null
if (-not (Test-Path "$PhysicalPath\index.html")) {
    Set-Content -Path "$PhysicalPath\index.html" -Encoding UTF8 -Value '<h1>Portal DZI</h1><p>Frontend w Etapie 3. Diagnostyka: <a href="/api/whoami">/api/whoami</a></p>'
}
if (-not (Get-Website -Name $SiteName -ErrorAction SilentlyContinue)) {
    New-Website -Name $SiteName -PhysicalPath $PhysicalPath -ApplicationPool $AppPoolName `
        -HostHeader $HostName -Port 443 -Ssl | Out-Null
    Write-Host '    utworzono witryne'
} else {
    Write-Host '    witryna juz istnieje'
}
try {
    $binding = Get-WebBinding -Name $SiteName -Protocol https
    $binding.AddSslCertificate($CertThumbprint, 'my')
    Write-Host '    certyfikat powiazany z bindingiem 443'
} catch {
    Write-Warning "Nie udalo sie powiazac certyfikatu: $($_.Exception.Message)"
    Write-Warning 'Sprawdz: thumbprint bez spacji, cert w LocalMachine\My, cert ma klucz prywatny.'
}

# --- 5. Uwierzytelnianie: Anonymous OFF, Windows ON, Negotiate przed NTLM ----
# Zapis przez -Location do applicationHost.config: dziala mimo domyslnej blokady
# sekcji auth (overrideModeDefault=Deny) — nie trzeba nic odblokowywac.
Step 'Konfiguruje uwierzytelnianie'
Set-WebConfigurationProperty -PSPath $apphost -Location $SiteName `
    -Filter 'system.webServer/security/authentication/anonymousAuthentication' -Name 'enabled' -Value 'False'
Set-WebConfigurationProperty -PSPath $apphost -Location $SiteName `
    -Filter 'system.webServer/security/authentication/windowsAuthentication' -Name 'enabled' -Value 'True'

$providersFilter = 'system.webServer/security/authentication/windowsAuthentication/providers'
foreach ($p in 'Negotiate', 'NTLM', 'Negotiate:Kerberos') {
    Remove-WebConfigurationProperty -PSPath $apphost -Location $SiteName `
        -Filter $providersFilter -Name '.' -AtElement @{ value = $p } -ErrorAction SilentlyContinue
}
Add-WebConfigurationProperty -PSPath $apphost -Location $SiteName -Filter $providersFilter -Name '.' -Value @{ value = 'Negotiate' }
Add-WebConfigurationProperty -PSPath $apphost -Location $SiteName -Filter $providersFilter -Name '.' -Value @{ value = 'NTLM' }
Write-Host '    providers: Negotiate, NTLM (w tej kolejnosci)'

# --- 6. web.config z regula proxy --------------------------------------------
Step 'Kopiuje web.config'
$source = Join-Path $PSScriptRoot 'web.config'
if (Test-Path $source) {
    Copy-Item $source -Destination (Join-Path $PhysicalPath 'web.config') -Force
    Write-Host "    $source -> $PhysicalPath\web.config"
} else {
    Write-Warning "Nie znaleziono $source — skopiuj web.config z repo recznie."
}

Write-Host ''
Write-Host 'GOTOWE. Nastepne kroki:' -ForegroundColor Green
Write-Host '  1. Upewnij sie, ze portal-api dziala na 127.0.0.1:8080 (konsola lub usluga).'
Write-Host '  2. Uruchom .\verify-etap1.ps1'
Write-Host '  3. Decydujacy test wykonaj Z INNEJ STACJI (nie z serwera).'
