/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.worker.execution;

import pl.dzi.portal.common.script.Script;

import java.util.List;

/**
 * Budowa linii poleceń dla typów skryptów. ZASADY BEZPIECZEŃSTWA:
 *  - ścieżka pochodzi WYŁĄCZNIE z whitelisty (tabela scripts), nigdy z żądania,
 *  - argumenty ZAWSZE jako osobne elementy listy (ProcessBuilder), nigdy sklejony string —
 *    spacje w ścieżkach i próby wstrzyknięcia przestają być tematem,
 *  - PS1: -NoProfile (deterministyczny start), -NonInteractive (żaden prompt nie zawiesi
 *    workera), -ExecutionPolicy Bypass (polityka per proces, bez zmian globalnych).
 */
final class ScriptCommands {

    private ScriptCommands() {
    }

    static List<String> commandFor(Script script) {
        return switch (script.scriptType()) {
            case PS1 -> List.of("powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-File", script.path());
            case BAT -> List.of("cmd.exe", "/c", script.path());
            case EXE -> List.of(script.path());
            case JAR -> List.of("java", "-jar", script.path()); // JDK w PATH usługi (README-WinSW)
        };
    }
}
