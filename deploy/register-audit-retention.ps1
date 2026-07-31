#Requires -RunAsAdministrator
# ======================================================================
# Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
# Autor: Maciej Myśliwiec, 2026.
# ======================================================================
<#
.SYNOPSIS
  Rejestruje cotygodniowe zadanie retencji (Task Scheduler; SQL Express nie ma Agenta).
  Zadanie wykonuje sqlcmd -E, wiec TOZSAMOSC ZADANIA musi miec GRANT DELETE
  na audit_log/task_log (patrz naglowek audit-retention.sql). NIE uzywaj gMSA aplikacji.

.EXAMPLE
  .\register-audit-retention.ps1 -RunAsUser 'DZI\svc-portal-maint' -Password (Read-Host -AsSecureString)
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$RunAsUser,
    [Parameter(Mandatory = $true)][SecureString]$Password,
    [string]$SqlScriptPath = 'D:\portal\maintenance\audit-retention.sql',
    [string]$TaskName = 'Portal-AuditRetention'
)
$ErrorActionPreference = 'Stop'

if (-not (Test-Path $SqlScriptPath)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $SqlScriptPath) | Out-Null
    Copy-Item (Join-Path $PSScriptRoot 'sql\audit-retention.sql') $SqlScriptPath
    Write-Host "Skopiowano skrypt retencji do $SqlScriptPath"
}

$action = New-ScheduledTaskAction -Execute 'sqlcmd.exe' `
    -Argument "-E -S localhost -d portal -b -i `"$SqlScriptPath`""
$trigger = New-ScheduledTaskTrigger -Weekly -DaysOfWeek Sunday -At 03:00
$plain = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Password))

Register-ScheduledTask -TaskName $TaskName -TaskPath '\Portal\' -Action $action -Trigger $trigger `
    -User $RunAsUser -Password $plain -RunLevel Limited -Force | Out-Null
Write-Host "Zarejestrowano zadanie \Portal\$TaskName (niedziela 03:00, konto $RunAsUser)" -ForegroundColor Green
Write-Host 'Pamietaj o jednorazowym GRANT DELETE dla tego konta - naglowek audit-retention.sql.'
