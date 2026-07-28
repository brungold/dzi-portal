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

import org.junit.jupiter.api.Test;
import pl.dzi.portal.common.script.Script;
import pl.dzi.portal.common.script.ScriptType;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptCommandsTest {

    @Test
    void should_build_powershell_command_with_safety_flags_and_path_as_separate_element() {
        var script = new Script(1L, "etl", "D:\\portal\\scripts\\z spacja\\etl.ps1", ScriptType.PS1, null, 300, true);

        var command = ScriptCommands.commandFor(script);

        assertThat(command).containsExactly("powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-File", "D:\\portal\\scripts\\z spacja\\etl.ps1");
    }

    @Test
    void should_build_cmd_wrapper_for_bat() {
        var script = new Script(1L, "x", "D:\\portal\\scripts\\x.bat", ScriptType.BAT, null, 300, true);

        assertThat(ScriptCommands.commandFor(script))
                .containsExactly("cmd.exe", "/c", "D:\\portal\\scripts\\x.bat");
    }

    @Test
    void should_run_exe_directly_and_jar_through_java() {
        var exe = new Script(1L, "x", "D:\\tools\\narzedzie.exe", ScriptType.EXE, null, 300, true);
        var jar = new Script(2L, "y", "D:\\tools\\narzedzie.jar", ScriptType.JAR, null, 300, true);

        assertThat(ScriptCommands.commandFor(exe)).containsExactly("D:\\tools\\narzedzie.exe");
        assertThat(ScriptCommands.commandFor(jar)).containsExactly("java", "-jar", "D:\\tools\\narzedzie.jar");
    }
}
