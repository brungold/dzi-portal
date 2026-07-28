# ======================================================================
# Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
# Autor: Maciej Myśliwiec, 2026.
# Autorskie prawa osobiste (prawo do autorstwa) niezbywalne — art. 16 pr. aut.
# Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
# ======================================================================
# Demo: skrypt konczy sie bledem — stderr trafia do task_log jako STDERR.
Write-Output "Probuje wykonac operacje..."
Write-Error "Symulowany blad: brak pliku wejsciowego"
exit 3
