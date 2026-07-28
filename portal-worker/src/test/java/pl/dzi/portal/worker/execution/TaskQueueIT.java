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

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.dzi.portal.common.task.TaskStatus;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dowód atomowości claim na PRAWDZIWYM SQL Server: dwóch "workerów" równolegle,
 * każdy dostaje INNE zadanie (READPAST + UPDLOCK), kolejność po id, trzeci claim pusty.
 * Migracje z portal-api ładowane lokalizacją filesystem (worker nie ma ich na classpath).
 */
@Testcontainers(disabledWithoutDocker = true)
class TaskQueueIT {

    @Container
    private static final MSSQLServerContainer<?> MSSQL =
            new MSSQLServerContainer<>(DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest"))
                    .acceptLicense();

    private static JdbcTemplate jdbc;
    private static TaskQueue queue;

    @BeforeAll
    static void migrateAndSeed() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MSSQL.getJdbcUrl(), MSSQL.getUsername(), MSSQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE portal_it");
        }
        String databaseUrl = MSSQL.getJdbcUrl() + ";databaseName=portal_it";

        Flyway.configure()
                .dataSource(databaseUrl, MSSQL.getUsername(), MSSQL.getPassword())
                .locations("filesystem:../portal-api/src/main/resources/db/migration")
                .load()
                .migrate();

        jdbc = new JdbcTemplate(new DriverManagerDataSource(databaseUrl, MSSQL.getUsername(), MSSQL.getPassword()));
        queue = new TaskQueue(jdbc, Clock.systemUTC());

        jdbc.update("INSERT INTO scripts (code, path, script_type, timeout_seconds, active) "
                + "VALUES ('it-script', 'C:/x.ps1', 'PS1', 5, 1)");
        Long scriptId = jdbc.queryForObject("SELECT id FROM scripts WHERE code = 'it-script'", Long.class);
        for (int i = 0; i < 2; i++) {
            jdbc.update("INSERT INTO tasks (script_id, requested_by, status, correlation_id, created_at) "
                            + "VALUES (?, 'it-user', 'PENDING', ?, SYSUTCDATETIME())",
                    scriptId, UUID.randomUUID().toString());
        }
    }

    @Test
    void should_hand_each_parallel_worker_a_different_task_oldest_first() throws Exception {
        Callable<Optional<ClaimedTask>> worker = () -> queue.claimNext("host-" + Thread.currentThread().getName());
        List<Future<Optional<ClaimedTask>>> futures;
        try (var executor = Executors.newFixedThreadPool(2)) {
            futures = executor.invokeAll(List.of(worker, worker));
        }

        long firstId = futures.get(0).get().orElseThrow().id();
        long secondId = futures.get(1).get().orElseThrow().id();

        assertThat(firstId).isNotEqualTo(secondId);
        assertThat(queue.claimNext("host-3")).as("trzeci claim: kolejka pusta").isEmpty();
        Integer inProgress = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tasks WHERE status = 'IN_PROGRESS'", Integer.class);
        assertThat(inProgress).isEqualTo(2);
    }

    @Test
    void should_finish_task_and_ignore_second_finish_after_sweeper_race() {
        jdbc.update("INSERT INTO tasks (script_id, requested_by, status, correlation_id, created_at, started_at) "
                + "SELECT id, 'it-user', 'IN_PROGRESS', ?, SYSUTCDATETIME(), SYSUTCDATETIME() "
                + "FROM scripts WHERE code = 'it-script'", UUID.randomUUID().toString());
        Long taskId = jdbc.queryForObject("SELECT MAX(id) FROM tasks", Long.class);

        queue.markFinished(taskId, TaskStatus.SUCCEEDED, 0);
        queue.markFinished(taskId, TaskStatus.FAILED, 1); // druga próba: guard statusu, brak nadpisania

        String status = jdbc.queryForObject("SELECT status FROM tasks WHERE id = ?", String.class, taskId);
        assertThat(status).isEqualTo("SUCCEEDED");
    }

    @Test
    void should_sweep_orphaned_in_progress_task_past_timeout_plus_margin() {
        jdbc.update("INSERT INTO tasks (script_id, requested_by, status, correlation_id, created_at, started_at) "
                + "SELECT id, 'it-user', 'IN_PROGRESS', ?, SYSUTCDATETIME(), DATEADD(SECOND, -300, SYSUTCDATETIME()) "
                + "FROM scripts WHERE code = 'it-script'", UUID.randomUUID().toString());
        Long orphanId = jdbc.queryForObject("SELECT MAX(id) FROM tasks", Long.class);

        List<Long> swept = queue.sweepOrphans();

        assertThat(swept).contains(orphanId);
        String status = jdbc.queryForObject("SELECT status FROM tasks WHERE id = ?", String.class, orphanId);
        assertThat(status).isEqualTo("TIMED_OUT");
    }
}
