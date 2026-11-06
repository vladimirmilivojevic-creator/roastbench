package dev.roastbench.common;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import dev.roastbench.AbstractIntegrationTest;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

class OpenApiIT extends AbstractIntegrationTest {

    @Test
    void publishesAnOpenApiDocumentWithTheMainEndpoints() {
        JsonPath doc = given().get("/v3/api-docs").then().statusCode(200).extract().jsonPath();

        assertThat(doc.getString("openapi")).startsWith("3.");
        assertThat(doc.getString("info.title")).isEqualTo("RoastBench API");
        assertThat(doc.getMap("paths").keySet()).contains("/api/roasts", "/api/roasts/import",
                "/api/roasts/import/preview", "/api/roasts/{id}/comparison", "/api/roasts/compare", "/api/lots",
                "/api/lots/{id}/movements", "/api/inventory/alerts", "/api/profiles", "/api/dashboard");
        assertThat(doc.getMap("paths").keySet()).doesNotContain("/api/admin/demo/reset");
        assertThat(doc.getList("tags.name")).contains("Roasts", "Green inventory", "Profiles");
    }

    @Test
    void servesSwaggerUi() {
        given().redirects().follow(true).get("/swagger-ui.html").then().statusCode(200);
    }

    @Test
    void reportsHealth() {
        given().get("/actuator/health").then().statusCode(200).body("status", equalTo("UP"));
    }
}
