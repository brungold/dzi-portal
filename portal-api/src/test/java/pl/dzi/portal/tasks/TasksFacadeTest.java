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

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import pl.dzi.portal.common.script.Script;
import pl.dzi.portal.common.task.TaskLogRepository;
import pl.dzi.portal.infrastructure.security.PortalUser;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TasksFacadeTest {

    private final TasksEndpointTest.InMemoryTaskRepository tasks = new TasksEndpointTest.InMemoryTaskRepository();
    private final TaskLogRepository emptyLog = (taskId, afterId) -> List.of();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-07T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void should_reject_tile_without_active_script_as_conflict() {
        var facade = new TasksFacade(emptyWhitelist(), tasks, emptyLog, clock);

        assertThatThrownBy(() -> facade.submit("etl-restart", user("admin"), "cid-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
        assertThat(tasks.countByStatus("PENDING")).isZero();
    }

    private static RunnableScriptRepository emptyWhitelist() {
        return new RunnableScriptRepository() {
            @Override public Optional<Script> findActiveScriptForTile(String tileCode) { return Optional.empty(); }
            @Override public Optional<Long> findTileId(String tileCode) { return Optional.empty(); }
        };
    }

    private static PortalUser user(String login) {
        return new PortalUser(login, Set.of("DZI-Portal-Admin"));
    }
}
