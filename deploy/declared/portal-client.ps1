# ======================================================================
# Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
# Autor: Maciej Myśliwiec, 2026.
# Autorskie prawa osobiste (prawo do autorstwa) niezbywalne — art. 16 pr. aut.
# Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
# ======================================================================
<#
.SYNOPSIS
  Klient portalu w trybie deklarowanym (ADR-0005): login i skrót departamentu
  czyta z AD dla ZALOGOWANEGO użytkownika (samaccountname + extensionattribute12)
  i dokłada jako nagłówki X-Auth-User / X-Auth-Dept do wywołań /api.

  Użytkownik niczego nie wpisuje. Skrypt niczego nie rozstrzyga po swojej
  stronie — o dostępie do każdego zasobu decyduje SERWER (tile_permissions);
  odmowa to 403, limiter to 429 (odczekać, nie pętlić).

.EXAMPLE
  .\portal-client.ps1                          # lista Twoich kafelków
  .\portal-client.ps1 -Run etl-restart         # uruchom kafelek SCRIPT i śledź wynik
  .\portal-client.ps1 -BaseUrl http://127.0.0.1:8080   # test lokalnie na serwerze
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'https://portal.dzi.pl',
    [string]$Run = ''
)
$ErrorActionPreference = 'Stop'

# --- tożsamość z AD (odczyt własnego konta — dostępny każdemu w domenie) ---
$props = ([adsisearcher]"(samaccountname=$env:USERNAME)").FindOne().Properties
$login = $props['samaccountname'][0].ToString().ToLowerInvariant()
$dept  = ''
if ($props.Contains('extensionattribute12') -and $props['extensionattribute12'].Count -gt 0) {
    $dept = $props['extensionattribute12'][0].ToString().ToLowerInvariant()
}
if (-not $dept) {
    Write-Warning "Brak skrótu departamentu (extensionattribute12) na Twoim koncie AD - zobaczysz tylko kafelki 'wszyscy' i imienne."
}

$headers = @{ 'X-Auth-User' = $login }
if ($dept) { $headers['X-Auth-Dept'] = $dept }
Write-Host "Deklaracja: $login / $(if ($dept) { $dept } else { '(bez departamentu)' }) -> $BaseUrl"

# --- lista kafelków przyciętych przez serwer do tej deklaracji ---
$tiles = Invoke-RestMethod -Uri "$BaseUrl/api/tiles" -Headers $headers
if (-not $tiles) {
    Write-Host 'Brak kafelków dla tej deklaracji.'
} else {
    $tiles | ForEach-Object { Write-Host (" - {0}  [{1}]  {2}" -f $_.code, $_.tileType, $_.name) }
}

# --- opcjonalnie: uruchomienie kafelka SCRIPT i polling wyniku ---
if ($Run) {
    $task = Invoke-RestMethod -Uri "$BaseUrl/api/tiles/$Run/run" -Method Post -Headers $headers
    Write-Host "Zlecono zadanie #$($task.taskId) - czekam na wynik..."
    do {
        Start-Sleep -Seconds 3
        $state = Invoke-RestMethod -Uri "$BaseUrl/api/tasks/$($task.taskId)" -Headers $headers
    } while ($state.status -notin 'SUCCEEDED','FAILED','TIMED_OUT','CANCELLED')
    Write-Host "Zadanie #$($state.id): $($state.status)"
    if ($state.status -ne 'SUCCEEDED') {
        (Invoke-RestMethod -Uri "$BaseUrl/api/tasks/$($state.id)/log" -Headers $headers) |
            ForEach-Object { Write-Host ("[{0}] {1}" -f $_.stream, $_.line) }
    }
}
