package dev.roastbench;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Starts the API for local development without Docker and without installing PostgreSQL: a real PostgreSQL
 * (zonky embedded binaries) keeps its data in {@code backend/.dev-data} and the demo data is seeded on first start.
 *
 * <p>Run with {@code ./mvnw spring-boot:test-run} from the {@code backend} folder.
 */
public final class DevApplication {

    private DevApplication() {
    }

    public static void main(String[] args) {
        System.setProperty("roastbench.demo.seed-on-startup", "true");
        SpringApplication.from(RoastBenchApplication::main).with(DevDatabase.class).run(args);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DevDatabase {

        @Bean(destroyMethod = "close")
        EmbeddedPostgres embeddedPostgres() throws IOException {
            return EmbeddedPostgres.builder()
                    .setDataDirectory(Path.of(".dev-data", "postgres"))
                    .setCleanDataDirectory(false)
                    .setPort(54329)
                    .start();
        }

        @Bean
        @Primary
        DataSource dataSource(EmbeddedPostgres postgres) {
            return postgres.getPostgresDatabase();
        }
    }
}
