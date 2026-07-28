# ======================================================================
# Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
# Autor: Maciej Myśliwiec, 2026.
# Autorskie prawa osobiste (prawo do autorstwa) niezbywalne — art. 16 pr. aut.
# Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
# ======================================================================
# Demo: przekroczenie limitu (timeout_seconds=5 w whitelist) — worker ubija drzewo procesow.
Write-Output "Zaczynam dluga operacje (30 s)..."
Start-Sleep -Seconds 30
Write-Output "Tego nigdy nie zobaczysz"
exit 0
