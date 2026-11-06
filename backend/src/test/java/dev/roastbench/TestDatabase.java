package dev.roastbench;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One PostgreSQL per test JVM, shared by all integration tests.
 *
 * <p>{@code -Droastbench.test.db=embedded} (default) starts real PostgreSQL binaries in-process, which needs no
 * Docker and little memory: this is what runs on a low-end laptop. {@code -Droastbench.test.db=testcontainers}
 * starts a {@code postgres:16-alpine} container: this is what CI runs.
 */
public final class TestDatabase {

    private static TestDatabase instance;

    private final String jdbcUrl;
    private final String username;
    private final String password;

    private TestDatabase() {
        String mode = System.getProperty("roastbench.test.db", "embedded");
        if ("testcontainers".equals(mode)) {
            PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine");
            container.start(); // stopped by Testcontainers' Ryuk when the JVM exits
            jdbcUrl = container.getJdbcUrl();
            username = container.getUsername();
            password = container.getPassword();
        } else {
            try {
                EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        postgres.close();
                    } catch (IOException ignored) {
                        // JVM is exiting
                    }
                }));
                jdbcUrl = postgres.getJdbcUrl("postgres", "postgres");
                username = "postgres";
                password = "postgres";
            } catch (IOException e) {
                throw new IllegalStateException("Could not start embedded PostgreSQL", e);
            }
        }
        System.out.println("[tests] database mode: " + mode);
    }

    public static synchronized TestDatabase get() {
        if (instance == null) {
            instance = new TestDatabase();
        }
        return instance;
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }
}
