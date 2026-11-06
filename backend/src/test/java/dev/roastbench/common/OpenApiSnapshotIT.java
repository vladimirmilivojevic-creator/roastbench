package dev.roastbench.common;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.roastbench.AbstractIntegrationTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Keeps {@code docs/openapi.json} in step with the code. When an endpoint changes, this test fails until the
 * snapshot is regenerated with {@code ./mvnw verify -Dopenapi.update=true} and committed, so the published API
 * contract can never silently drift from the implementation.
 */
class OpenApiSnapshotIT extends AbstractIntegrationTest {

    private static final Path SNAPSHOT = Path.of("..", "docs", "openapi.json");

    @Test
    @SuppressWarnings("unchecked")
    void apiDocumentMatchesTheCommittedSnapshot() throws IOException {
        String live = given().get("/v3/api-docs").then().statusCode(200).extract().asString();
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        Map<String, Object> document = mapper.readValue(live, Map.class);
        document.remove("servers"); // contains the random test port
        // Jackson indents with the platform line separator; the snapshot always uses LF.
        String normalised = mapper.writeValueAsString(document).replace("\r\n", "\n") + "\n";

        if (Boolean.getBoolean("openapi.update")) {
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(SNAPSHOT, normalised, StandardCharsets.UTF_8);
            return;
        }
        assertThat(SNAPSHOT).as("docs/openapi.json is missing; run ./mvnw verify -Dopenapi.update=true").exists();
        String committed = Files.readString(SNAPSHOT, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(normalised)
                .as("docs/openapi.json is out of date; run ./mvnw verify -Dopenapi.update=true and commit it")
                .isEqualTo(committed);
    }
}
