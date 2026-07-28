/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.tasks;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.dzi.portal.common.task.TaskLogRepository;
import pl.dzi.portal.common.task.TaskRepository;

import java.time.Clock;

@Configuration
class TasksConfiguration {

    @Bean
    TasksFacade tasksFacade(RunnableScriptRepository runnableScripts, TaskRepository tasks,
                            TaskLogRepository taskLog, Clock clock) {
        return new TasksFacade(runnableScripts, tasks, taskLog, clock);
    }
}
