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

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Agregat kolejki zadań. Niemutowalny rekord — zmiany stanu (claim, statusy) będą
 * wykonywane jawnym SQL w TaskRepository (Etap 4), nie przez modyfikację obiektu.
 * Czas wyłącznie w UTC (kolumny DATETIME2).
 */
@Table("tasks")
public record Task(
        @Id Long id,
        Long scriptId,
        Long tileId,
        String requestedBy,
        String params,
        TaskStatus status,
        String correlationId,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Integer exitCode,
        String workerHost,
        @Version int version) {

    /** Nowe zadanie do kolejki. createdAt ustawiamy w kodzie (nie DEFAULT-em w bazie), żeby Clock był jedynym źródłem czasu. */
    public static Task pending(long scriptId, Long tileId, String requestedBy, String paramsJson,
                               String correlationId, Instant now) {
        return new Task(null, scriptId, tileId, requestedBy, paramsJson,
                TaskStatus.PENDING, correlationId, now, null, null, null, null, 0);
    }
}
