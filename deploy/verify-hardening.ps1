#Requires -RunAsAdministrator
# ======================================================================
# Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
# Autor: Maciej Myśliwiec, 2026.
# ======================================================================
<#
.SYNOPSIS
  Etap 6: przeglad uprawnien i konfiguracji po hardening. PASS/FAIL jak verify-etap1.
  Kluczowa granica: gMSA workera NIE MOZE pisac do katalogu skryptow -
  inaczej "wykonaj skrypt z whitelisty" staje sie "wykonaj cokolwiek".
#>
[CmdletBinding()]
param(
    [string]$ApiAccount = 'DZI\gMSA-PortalApi$',
    [string]$WorkerAccount = 'DZI\gMSA-PortalWorker$',
    [string]$ScriptsDir = 'D:\portal\scripts',
    [string]$ApiConfig = 'D:\portal\api\portal-api.xml'
)
$ErrorActionPreference = 'Continue'
$script:failures = 0
function OK($m)   { Write-Host "[ OK ] $m" -ForegroundColor Green }
function WARN($m) { Write-Host "[WARN] $m" -ForegroundColor Yellow }
function FAIL($m) { Write-Host "[FAIL] $m" -ForegroundColor Red; $script:failures++ }

Write-Host '=== Przeglad hardeningowy portalu ===' -ForegroundColor Cyan

# 1. Konta uslug
foreach ($pair in @(@('portal-api', $ApiAccount), @('portal-worker', $WorkerAccount))) {
    $name, $expected = $pair
    $service = Get-CimInstance Win32_Service -Filter "Name='$name'" -ErrorAction SilentlyContinue
    if (-not $service) { FAIL "usluga $name nie istnieje"; continue }
    if ($service.StartName -eq $expected) { OK "$name dziala jako $expected" }
    else { FAIL "$name dziala jako '$($service.StartName)', oczekiwano $expected" }
    if ($service.StartMode -eq 'Auto') { OK "$name start: Automatic" } else { WARN "$name start: $($service.StartMode)" }
}

# 2. gMSA poza lokalnymi administratorami
# BUILTIN\Administrators po SID (S-1-5-32-544): na polskim Windows grupa nazywa sie
# "Administratorzy" i dopasowanie po nazwie cicho by NIC nie znalazlo (poprawka, commit 31).
$admins = Get-LocalGroupMember -SID 'S-1-5-32-544' -ErrorAction SilentlyContinue
foreach ($account in @($ApiAccount, $WorkerAccount)) {
    if ($admins | Where-Object Name -eq $account) { FAIL "$account jest lokalnym administratorem" }
    else { OK "$account nie jest lokalnym administratorem" }
}

# 3. Bind API wylacznie na loopbacku
$listen = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if (-not $listen) { WARN 'port 8080 nie nasluchuje (api zatrzymane?)' }
elseif ($listen | Where-Object { $_.LocalAddress -notin '127.0.0.1', '::1' }) {
    FAIL 'API nasluchuje poza loopbackiem! Sprawdz server.address w profilu prod.'
} else { OK 'API nasluchuje wylacznie na 127.0.0.1' }

# 4. NTFS: katalog skryptow bez prawa zapisu dla workera (granica RCE)
if (Test-Path $ScriptsDir) {
    $writeRights = 'Write|Modify|FullControl|CreateFiles|AppendData'
    $bad = (Get-Acl $ScriptsDir).Access | Where-Object {
        $_.IdentityReference -eq $WorkerAccount -and $_.AccessControlType -eq 'Allow' `
            -and ($_.FileSystemRights.ToString() -match $writeRights)
    }
    if ($bad) { FAIL "$WorkerAccount ma prawo zapisu do $ScriptsDir - moze podmienic whiteliste na dysku!" }
    else { OK "$WorkerAccount bez zapisu do $ScriptsDir (tylko odczyt/wykonanie)" }
} else { WARN "$ScriptsDir nie istnieje" }

# 5. ACL pliku z sekretem LDAP
if (Test-Path $ApiConfig) {
    # BUILTIN\Users po SID (S-1-5-32-545): dopasowanie po nazwie pominieoby polskie
    # "Uzytkownicy" z ogonkiem (dokonczenie poprawki lokalizacyjnej z 31, commit 32)
    $usersRead = (Get-Acl $ApiConfig).Access | Where-Object {
        if ($_.AccessControlType -ne 'Allow') { return $false }
        try {
            $_.IdentityReference.Translate([System.Security.Principal.SecurityIdentifier]).Value -eq 'S-1-5-32-545'
        } catch { $false }
    }
    if ($usersRead) { FAIL "BUILTIN\Users czyta $ApiConfig (haslo LDAP!) - icacls wg README-WinSW" }
    else { OK "ACL $ApiConfig bez BUILTIN\Users" }
} else { WARN "$ApiConfig nie istnieje" }

# 6. SQL: DENY append-only i brak ddladmin u workera (wymaga sqlcmd + uprawnien operatora)
try {
    $deny = & sqlcmd -E -S localhost -d portal -h -1 -W -Q `
        "SET NOCOUNT ON; SELECT COUNT(*) FROM sys.database_permissions p JOIN sys.database_principals u ON u.principal_id=p.grantee_principal_id WHERE p.state='D' AND p.permission_name IN ('UPDATE','DELETE') AND OBJECT_NAME(p.major_id)='audit_log'" 2>$null
    if ([int]($deny | Select-Object -First 1) -ge 4) { OK 'SQL: DENY UPDATE/DELETE na audit_log obecne (>=4 wpisy)' }
    else { FAIL 'SQL: brak kompletu DENY na audit_log - uruchom prod-grants.sql' }
} catch { WARN 'SQL: pominieto (sqlcmd/uprawnienia operatora)' }

# 7. Zadanie retencji
if (Get-ScheduledTask -TaskName 'Portal-AuditRetention' -TaskPath '\Portal\' -ErrorAction SilentlyContinue) {
    OK 'Zadanie retencji \Portal\Portal-AuditRetention zarejestrowane'
} else { WARN 'Brak zadania retencji - register-audit-retention.ps1' }

Write-Host ''
if ($script:failures -eq 0) { Write-Host '=== HARDENING OK ===' -ForegroundColor Green }
else { Write-Host "=== Bledow: $script:failures ===" -ForegroundColor Red }
