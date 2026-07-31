# ======================================================================
# Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
# Autor: Maciej Myśliwiec, 2026.
# ======================================================================
# Demo: poprawne zakonczenie. Worker przekazuje kontekst w env.
Write-Output "Start zadania $env:PORTAL_TASK_ID (correlation: $env:PORTAL_CORRELATION_ID)"
1..3 | ForEach-Object {
    Write-Output "Krok $_ z 3"
    Start-Sleep -Seconds 1
}
Write-Output "Zakonczono poprawnie"
exit 0
