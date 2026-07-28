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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pl.dzi.portal.common.script.Script;
import pl.dzi.portal.common.script.ScriptType;
import pl.dzi.portal.common.task.Task;
import pl.dzi.portal.common.task.TaskLogEntry;
import pl.dzi.portal.common.task.TaskLogRepository;
import pl.dzi.portal.common.task.TaskRepository;
import pl.dzi.portal.infrastructure.audit.AuditContext;
import pl.dzi.portal.infrastructure.security.AdGroupResolver;
import pl.dzi.portal.infrastructure.security.SecurityConfig;
import pl.dzi.portal.tiles.InMemoryTilesRepositories;
import pl.dzi.portal.tiles.TilesConfiguration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.dzi.portal.testsupport.TestRequests.asUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Plasterkowo przez prawdziwy łańcuch security + AccessFacade:
 *  - zlecenie z EXECUTE => 202 + zadanie w "bazie" z loginem zlecającego,
 *  - zlecenie z samym READ => twarde 403 (drugi poziom RBAC),
 *  - status/log cudzego zadania => 403 (właścicielstwo),
 *  - kafelek bez skryptu => 409 (rozjazd konfiguracji, nie uprawnień).
 */
@WebMvcTest(controllers = TasksController.class)
@Import({SecurityConfig.class, TilesConfiguration.class, TasksConfiguration.class,
        AuditContext.class, TasksEndpointTest.StubBeans.class})
class TasksEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryTaskRepository taskRepository;

    @BeforeEach
    void freshQueue() {
        // Izolacja (FIRST): beany-double'y są singletonami kontekstu plasterka —
        // bez resetu wynik metody zależałby od kolejności testów (poprawka, commit 31).
        taskRepository.reset();
    }

    @TestConfiguration
    static class StubBeans {

        @Bean
        AdGroupResolver adGroupResolver() {
            return samAccountName -> switch (samAccountName) {
                case "admin" -> Set.of("DZI-Portal-Admin");
                case "viewer" -> Set.of("DZI-Portal-Raporty-Odczyt");
                default -> Set.of();
            };
        }

        @Bean
        pl.dzi.portal.tiles.TileRepository tileRepository() {
            return new InMemoryTilesRepositories.InMemoryTileRepository(
                    InMemoryTilesRepositories.sampleTiles(), InMemoryTilesRepositories.samplePermissions());
        }

        @Bean
        pl.dzi.portal.tiles.TilePermissionRepository tilePermissionRepository() {
            return new InMemoryTilesRepositories.InMemoryTilePermissionRepository(
                    InMemoryTilesRepositories.sampleTiles(), InMemoryTilesRepositories.samplePermissions());
        }

        @Bean
        RunnableScriptRepository runnableScriptRepository() {
            return new RunnableScriptRepository() {
                @Override
                public Optional<Script> findActiveScriptForTile(String tileCode) {
                    // etl-restart ma skrypt; pozostałe kafelki SCRIPT bez wpisu => 409
                    return "etl-restart".equals(tileCode)
                            ? Optional.of(new Script(10L, "etl-restart", "D:/x.ps1", ScriptType.PS1, null, 300, true))
                            : Optional.empty();
                }

                @Override
                public Optional<Long> findTileId(String tileCode) {
                    return Optional.of(1L);
                }
            };
        }

        @Bean
        InMemoryTaskRepository taskRepository() {
            return new InMemoryTaskRepository();
        }

        @Bean
        TaskLogRepository taskLogRepository() {
            return (taskId, afterId) -> List.of(
                    new TaskLogEntry(1L, taskId, Instant.parse("2026-07-07T10:00:00Z"), "STDOUT", "linia 1"));
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-07-07T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Test
    void should_accept_submission_with_execute_permission_and_store_requester() throws Exception {
        mockMvc.perform(asUser(post("/api/tiles/etl-restart/run"), "admin"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.taskId").isNumber())
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(taskRepository.saved).hasSize(1);
        assertThat(taskRepository.saved.get(0).requestedBy()).isEqualTo("admin");
        assertThat(taskRepository.saved.get(0).params()).as("parametry użytkownika wyłączone").isNull();
    }

    @Test
    void should_hard_block_submission_with_read_only_permission() throws Exception {
        mockMvc.perform(asUser(post("/api/tiles/etl-restart/run"), "viewer"))
                .andExpect(status().isForbidden());

        assertThat(taskRepository.saved).isEmpty();
    }

    @Test
    void should_return_403_for_foreign_task_status_and_log() throws Exception {
        Task saved = taskRepository.save(Task.pending(10L, 1L, "admin", null, "cid-x",
                Instant.parse("2026-07-07T10:00:00Z")));

        mockMvc.perform(asUser(get("/api/tasks/" + saved.id()), "viewer"))
                .andExpect(status().isForbidden());
        mockMvc.perform(asUser(get("/api/tasks/" + saved.id() + "/log"), "viewer"))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_return_status_and_log_to_owner() throws Exception {
        Task saved = taskRepository.save(Task.pending(10L, 1L, "admin", null, "cid-y",
                Instant.parse("2026-07-07T10:00:00Z")));

        mockMvc.perform(asUser(get("/api/tasks/" + saved.id()), "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.correlationId").value("cid-y"));

        mockMvc.perform(asUser(get("/api/tasks/" + saved.id() + "/log"), "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].line").value("linia 1"));
    }

    @Test
    void should_return_400_for_non_numeric_task_id() throws Exception {
        // MethodArgumentTypeMismatch: wcześniej catch-all zamieniał to w 500 (commit 32)
        mockMvc.perform(asUser(get("/api/tasks/abc"), "admin"))
                .andExpect(status().isBadRequest());
    }

    /** Double TaskRepository: nadaje id jak IDENTITY, pamięta zapisy. */
    static final class InMemoryTaskRepository implements TaskRepository {
        private final List<Task> saved = new ArrayList<>();
        private long nextId = 100;

        void reset() {
            saved.clear();
            nextId = 100;
        }

        @Override
        public Task save(Task task) {
            Task withId = new Task(nextId++, task.scriptId(), task.tileId(), task.requestedBy(),
                    task.params(), task.status(), task.correlationId(), task.createdAt(),
                    task.startedAt(), task.finishedAt(), task.exitCode(), task.workerHost(), task.version());
            saved.add(withId);
            return withId;
        }

        @Override
        public Optional<Task> findById(Long id) {
            return saved.stream().filter(task -> task.id().equals(id)).findFirst();
        }

        @Override
        public long countByStatus(String status) {
            return saved.stream().filter(task -> task.status().name().equals(status)).count();
        }
    }
}
