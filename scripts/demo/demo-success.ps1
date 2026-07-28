# ======================================================================
# Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
# Autor: Maciej Myśliwiec, 2026.
# Autorskie prawa osobiste (prawo do autorstwa) niezbywalne — art. 16 pr. aut.
# Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
# ======================================================================
# Demo: poprawne zakonczenie. Worker przekazuje kontekst w env.
Write-Output "Start zadania $env:PORTAL_TASK_ID (correlation: $env:PORTAL_CORRELATION_ID)"
1..3 | ForEach-Object {
    Write-Output "Krok $_ z 3"
    Start-Sleep -Seconds 1
}
Write-Output "Zakonczono poprawnie"
exit 0
