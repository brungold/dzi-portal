#Requires -RunAsAdministrator
# ======================================================================
# Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
# Autor: Maciej Myśliwiec, 2026.
# Autorskie prawa osobiste (prawo do autorstwa) niezbywalne — art. 16 pr. aut.
# Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
# ======================================================================
<#
.SYNOPSIS
  Pelny deploy portalu: stop uslug -> backup -> podmiana jarow + frontendu ->
  migracje (start api) -> smoke -> start workera. Kazda porazka = AUTOMATYCZNY
  rollback jarow i restart poprzedniej wersji.

.EXAMPLE
  .\deploy.ps1 -ApiJar ..\portal-api.jar -WorkerJar ..\portal-worker.jar -FrontendSource ..\frontend
  .\deploy.ps1 -RollbackOnly          # cwiczenie rollbacku / awaria po deployu
#>
[CmdletBinding()]
param(
    [string]$ApiJar,
    [string]$WorkerJar,
    [string]$FrontendSource,
    [string]$InstallRoot = 'D:\portal',
    [string]$WwwRoot = 'D:\portal\www',
    [switch]$RollbackOnly
)
$ErrorActionPreference = 'Stop'
$apiDir = Join-Path $InstallRoot 'api'
$workerDir = Join-Path $InstallRoot 'worker'

function Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }

function Stop-IfRunning([string]$name) {
    $service = Get-Service -Name $name -ErrorAction SilentlyContinue
    if ($service -and $service.Status -eq 'Running') {
        Stop-Service -Name $name
        $service.WaitForStatus('Stopped', [TimeSpan]::FromSeconds(45))
        Write-Host "    zatrzymano $name"
    }
}

function Start-Api {
    Start-Service -Name 'portal-api'
    $deadline = (Get-Date).AddSeconds(90)   # zapas na migracje Flyway
    do {
        Start-Sleep -Seconds 2
        try {
            $health = Invoke-RestMethod 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 3 -UseBasicParsing
            if ($health.status -eq 'UP') { return }
        } catch { }
    } while ((Get-Date) -lt $deadline)
    throw 'portal-api nie zglosil UP w 90 s'
}

function Invoke-Smoke {
    # whoami z naglowkiem z loopbacku: caly lancuch filtr->grupy->audyt zyje.
    $who = Invoke-RestMethod 'http://127.0.0.1:8080/api/whoami' -TimeoutSec 5 -UseBasicParsing `
        -Headers @{ 'X-Auth-User' = 'DZI\deploy-smoke' }
    if (-not $who.login) { throw 'smoke whoami: brak loginu w odpowiedzi' }
    try {
        $info = Invoke-RestMethod 'http://127.0.0.1:8080/actuator/info' -TimeoutSec 5 -UseBasicParsing
        Write-Host ("    build: {0} ({1})" -f $info.build.version, $info.build.time)
    } catch { Write-Host '    (brak build-info)' }
}

function Restore-Previous([string]$dir, [string]$jarName) {
    $previous = Join-Path $dir "$jarName.previous.jar"
    if (Test-Path $previous) {
        Copy-Item $previous (Join-Path $dir "$jarName.jar") -Force
        Write-Host "    przywrocono $jarName.previous.jar" -ForegroundColor Yellow
    } else {
        Write-Warning "brak $previous - rollback $jarName niemozliwy"
    }
}

function Start-Everything {
    Start-Api
    Invoke-Smoke
    Start-Service -Name 'portal-worker' -ErrorAction SilentlyContinue
    Write-Host 'Uslugi wystartowane.' -ForegroundColor Green
}

# ---------- ROLLBACK ONLY ----------
if ($RollbackOnly) {
    Step 'ROLLBACK: przywracam poprzednie jary'
    Stop-IfRunning 'portal-worker'; Stop-IfRunning 'portal-api'
    Restore-Previous $apiDir 'portal-api'
    Restore-Previous $workerDir 'portal-worker'
    Start-Everything
    exit 0
}

if (-not $ApiJar) { throw 'Podaj -ApiJar (albo uzyj -RollbackOnly)' }

# ---------- DEPLOY ----------
try {
    Step 'Zatrzymuje uslugi (worker przed api)'
    Stop-IfRunning 'portal-worker'
    Stop-IfRunning 'portal-api'

    Step 'Backup + podmiana jarow'
    foreach ($pair in @(@($ApiJar, $apiDir, 'portal-api'), @($WorkerJar, $workerDir, 'portal-worker'))) {
        $source, $dir, $name = $pair
        if (-not $source) { continue }
        if (-not (Test-Path $source)) { throw "Nie znaleziono: $source" }
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
        $target = Join-Path $dir "$name.jar"
        if (Test-Path $target) { Copy-Item $target (Join-Path $dir "$name.previous.jar") -Force }
        Copy-Item $source $target -Force
        Write-Host "    $name.jar podmieniony"
    }

    if ($FrontendSource) {
        Step "Frontend -> $WwwRoot (robocopy /MIR, web.config chroniony)"
        # /XF web.config: plik z regula proxy zyje TYLKO na serwerze - MIR go nie skasuje.
        robocopy $FrontendSource $WwwRoot /MIR /XF web.config /NFL /NDL /NJH /NJS | Out-Null
        if ($LASTEXITCODE -ge 8) { throw "robocopy zakonczyl sie kodem $LASTEXITCODE" }
        $global:LASTEXITCODE = 0
    }

    Step 'Start api (migracje Flyway) + smoke + start workera'
    Start-Everything
    Write-Host 'DEPLOY OK' -ForegroundColor Green
} catch {
    Write-Warning "DEPLOY NIEUDANY: $($_.Exception.Message)"
    Step 'Automatyczny rollback'
    Stop-IfRunning 'portal-worker'; Stop-IfRunning 'portal-api'
    Restore-Previous $apiDir 'portal-api'
    Restore-Previous $workerDir 'portal-worker'
    try { Start-Everything } catch { Write-Warning "Rollback tez nie wstal: $($_.Exception.Message)" }
    exit 1
}
