package dev.roastbench;

import static io.restassured.RestAssured.given;

import dev.roastbench.demo.SampleFormats;
import dev.roastbench.demo.SampleFormats.Meta;
import dev.roastbench.demo.SyntheticRoastGenerator;
import dev.roastbench.demo.SyntheticRoastGenerator.Spec;
import io.restassured.RestAssured;
import io.restassured.builder.MultiPartSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Boots the whole application on a random port against a real PostgreSQL and gives tests API helpers. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final Meta META = new Meta("Test roast", "Test beans", "Test machine",
            Instant.parse("2026-10-12T08:30:00Z"));

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase db = TestDatabase.get();
        registry.add("spring.datasource.url", db::jdbcUrl);
        registry.add("spring.datasource.username", db::username);
        registry.add("spring.datasource.password", db::password);
    }

    @BeforeEach
    void cleanDatabase() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
        jdbc.execute("truncate table stock_movement, roast_lot, roast_event, roast_curve, roast, profile, lot, "
                + "location restart identity cascade");
        jdbc.execute("alter sequence roast_batch_seq restart with 1");
    }

    // ---- inventory helpers ----------------------------------------------------------------------------------

    protected long createLocation(String name) {
        return given().contentType(ContentType.JSON).body(Map.of("name", name))
                .post("/api/locations").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    protected long createLot(String code, String country, double minStockKg) {
        return createLot(code, country, minStockKg, null);
    }

    protected long createLot(String code, String country, double minStockKg, Double pricePerKg) {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("code", code);
        body.put("name", code + " coffee");
        body.put("country", country);
        body.put("minStockKg", minStockKg);
        body.put("pricePerKg", pricePerKg);
        return given().contentType(ContentType.JSON).body(body)
                .post("/api/lots").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    protected Response receive(long lotId, long locationId, double kg) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("locationId", locationId, "quantityKg", kg, "reason", "Delivery"))
                .post("/api/lots/{id}/receipts", lotId);
    }

    protected double stockOf(long lotId) {
        return given().get("/api/lots/{id}", lotId).then().statusCode(200).extract().jsonPath().getDouble("stockKg");
    }

    protected long createProfile(String name) {
        return given().contentType(ContentType.JSON).body(Map.of("name", name))
                .post("/api/profiles").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    // ---- roast helpers --------------------------------------------------------------------------------------

    protected static String csvRoast(Spec spec) {
        return SampleFormats.toCsv(SyntheticRoastGenerator.generate(spec), META);
    }

    protected static String alogRoast(Spec spec) {
        return SampleFormats.toAlog(SyntheticRoastGenerator.generate(spec.withCoolingSeconds(120)), META);
    }

    protected Response preview(String filename, String content) {
        return given().multiPart("file", filename, content.getBytes(StandardCharsets.UTF_8), "text/plain")
                .post("/api/roasts/import/preview");
    }

    /** Imports a roast log; {@code request} may be {@code null} for "use what the file says". */
    protected Response importRoast(String filename, String content, Map<String, Object> request) {
        var spec = given().multiPart("file", filename, content.getBytes(StandardCharsets.UTF_8), "text/plain");
        if (request != null) {
            try {
                String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request);
                spec = spec.multiPart(new MultiPartSpecBuilder(json).controlName("request")
                        .mimeType("application/json").build());
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
        return spec.post("/api/roasts/import");
    }

    protected static Map<String, Object> lotWeight(long lotId, int grams) {
        return Map.of("lotId", lotId, "greenWeightG", grams);
    }
}
