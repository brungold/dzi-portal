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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.dzi.portal.common.script.ScriptRepository;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.time.Clock;

/**
 * Ręczne składanie komponentów egzekucji (te same zasady co fasady w api:
 * zwykłe klasy, zależności widać w jednym miejscu, testy robią new bez Springa).
 */
@Configuration
class WorkerConfiguration {

    @Bean
    TaskQueue taskQueue(JdbcTemplate jdbcTemplate, Clock clock) {
        return new TaskQueue(jdbcTemplate, clock);
    }

    @Bean
    TaskLogWriter taskLogWriter(JdbcTemplate jdbcTemplate) {
        return new TaskLogWriter(jdbcTemplate);
    }

    @Bean
    ProcessRunner processRunner(TaskLogWriter taskLogWriter,
                                @Value("${portal.worker.console-charset:Cp852}") String consoleCharset) {
        // Cp852 = OEM polskiego Windows (PowerShell 5.1 / cmd). Skrypty w UTF-8: zmień właściwość.
        return new ProcessRunner(taskLogWriter, Charset.forName(consoleCharset));
    }

    @Bean
    TaskExecutor taskExecutor(TaskQueue taskQueue, ScriptRepository scripts,
                              ProcessRunner processRunner, TaskLogWriter taskLogWriter) {
        return new TaskExecutor(taskQueue, scripts, processRunner, taskLogWriter, workerHost());
    }

    private static String workerHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-host";
        }
    }
}
