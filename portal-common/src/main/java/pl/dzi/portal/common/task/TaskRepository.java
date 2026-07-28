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

import java.util.Optional;

/**
 * Od Etapu 4: baza Repository z selektywnie wystawionymi metodami CRUD
 * (save/findById to sygnatury z CrudRepository — Spring Data podstawia implementację).
 * Powierzchnia = to, czego naprawdę używamy; in-memory double w testach ma 15 linii.
 *
 * Claim kolejki CELOWO nie jest tutaj: to T-SQL z hintami blokad (READPAST/UPDLOCK),
 * wykonywany wyłącznie przez workera — patrz worker/execution/TaskQueue.
 */
public interface TaskRepository extends Repository<Task, Long> {

    Task save(Task task);

    Optional<Task> findById(Long id);

    /** Jawny SQL zamiast derived query — od początku widać, co poleci do bazy. */
    @Query("SELECT COUNT(*) FROM tasks WHERE status = :status")
    long countByStatus(@Param("status") String status);
}
