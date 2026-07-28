#Requires -RunAsAdministrator
# ======================================================================
# Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
# Autor: Maciej Myśliwiec, 2026.
# Autorskie prawa osobiste (prawo do autorstwa) niezbywalne — art. 16 pr. aut.
# Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
# ======================================================================
<#
.SYNOPSIS
  Minimalny deploy portal-api: stop uslugi -> backup jara -> podmiana -> start -> health check.
  Podczas debugowania Etapu 1 bedziesz to robil czesto; pelny deploy.ps1 (z frontendem) w Etapie 6.

.EXAMPLE
  .\deploy-api.ps1 -JarSource '\\stacja\wymiana\portal-api-0.1.0-SNAPSHOT.jar'
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$JarSource,
    [string]$InstallDir  = 'D:\portal\api',
    [string]$ServiceName = 'portal-api',
    [string]$HealthUrl   = 'http://127.0.0.1:8080/actuator/health'
)
$ErrorActionPreference = 'Stop'

if (-not (Test-Path $JarSource)) { throw "Nie znaleziono jara: $JarSource" }
New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
$target = Join-Path $InstallDir 'portal-api.jar'

$service = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if ($service -and $service.Status -eq 'Running') {
    Write-Host "Zatrzymuje usluge $ServiceName..."
    Stop-Service -Name $ServiceName
    $service.WaitForStatus('Stopped', [TimeSpan]::FromSeconds(40))
}

if (Test-Path $target) {
    Copy-Item $target (Join-Path $InstallDir 'portal-api.previous.jar') -Force
    Write-Host 'Backup: portal-api.previous.jar'
}
Copy-Item $JarSource $target -Force
Write-Host "Wgrano: $target"

if ($service) {
    Start-Service -Name $ServiceName
    Write-Host 'Czekam na health...'
    $deadline = (Get-Date).AddSeconds(60)
    do {
        Start-Sleep -Seconds 2
        try {
            $health = Invoke-RestMethod -Uri $HealthUrl -TimeoutSec 3 -UseBasicParsing
            if ($health.status -eq 'UP') { Write-Host 'UP — deploy zakonczony.' -ForegroundColor Green; exit 0 }
        } catch { }
    } while ((Get-Date) -lt $deadline)

    Write-Warning 'Aplikacja nie zglosila UP w 60 s. Ostatnie linie logu wrappera:'
    Get-ChildItem $InstallDir -Filter '*.wrapper.log' | ForEach-Object { Get-Content $_.FullName -Tail 30 }
    Write-Warning "Rollback: Stop-Service $ServiceName; Copy-Item $InstallDir\portal-api.previous.jar $target -Force; Start-Service $ServiceName"
    exit 1
} else {
    Write-Host "Uslugi $ServiceName jeszcze nie ma (Faza A) — uruchom recznie w konsoli wg runbooka." -ForegroundColor Yellow
}
