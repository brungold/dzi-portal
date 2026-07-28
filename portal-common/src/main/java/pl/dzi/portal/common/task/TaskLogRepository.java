/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.common.task;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskLogRepository extends Repository<TaskLogEntry, Long> {

    /**
     * Odczyt przyrostowy pod polling UI: klient podaje id ostatniej znanej linii
     * i dostaje wyłącznie nowsze — zero przesyłania całego logu co 2,5 sekundy.
     */
    @Query("SELECT * FROM task_log WHERE task_id = :taskId AND id > :afterId ORDER BY id")
    List<TaskLogEntry> findByTaskIdAfter(@Param("taskId") long taskId, @Param("afterId") long afterId);
}
