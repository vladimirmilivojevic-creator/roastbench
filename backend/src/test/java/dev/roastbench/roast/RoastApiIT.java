package dev.roastbench.roast;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import dev.roastbench.AbstractIntegrationTest;
import dev.roastbench.demo.SyntheticRoastGenerator.Spec;
import io.restassured.path.json.JsonPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoastApiIT extends AbstractIntegrationTest {

    private static final Spec MEDIUM_5KG = Spec.medium(1).withGreenWeightG(5000);

    private long warehouse;

    @BeforeEach
    void createWarehouse() {
        warehouse = createLocation("Warehouse");
    }

    @Test
    void previewParsesACsvWithoutSavingAnything() {
        JsonPath p = preview("medium.csv", csvRoast(MEDIUM_5KG)).then().statusCode(200).extract().jsonPath();

        assertThat(p.getString("format")).isEqualTo("CSV");
        assertThat(p.getString("title")).isEqualTo("Test roast");
        assertThat(p.getDouble("greenWeightG")).isEqualTo(5000.0);
        assertThat(p.getDouble("metrics.developmentTimeRatioPercent")).isBetween(13.0, 20.0);
        assertThat(p.getDouble("metrics.roastLossPercent")).isCloseTo(14.5, within(0.1));
        assertThat(p.getList("events.type")).contains("CHARGE", "TURNING_POINT", "DRY_END", "FIRST_CRACK_START",
                "FIRST_CRACK_END", "DROP");
        assertThat(p.getList("curve.timeSeconds")).hasSizeGreaterThan(200);
        assertThat(p.getList("curve.ror")).hasSameSizeAs(p.getList("curve.beanTemp"));
        assertThat(p.getList("metrics.phases.phase")).containsExactly("DRYING", "MAILLARD", "DEVELOPMENT");

        given().get("/api/roasts").then().statusCode(200).body("totalItems", equalTo(0));
    }

    @Test
    void previewReadsAnArtisanProfileAndTrimsTheCooling() {
        JsonPath p = preview("medium.alog", alogRoast(MEDIUM_5KG)).then().statusCode(200).extract().jsonPath();

        assertThat(p.getString("format")).isEqualTo("ARTISAN_ALOG");
        assertThat(p.getString("machine")).isEqualTo("Test machine");
        assertThat(p.getList("warnings", String.class)).anyMatch(w -> w.contains("after drop"));
        assertThat(p.getList("events.type")).doesNotContain("COOL");
        List<Double> time = p.getList("curve.timeSeconds", Double.class);
        assertThat(time.get(0)).isZero();
        assertThat(time.get(time.size() - 1)).isEqualTo(p.getDouble("metrics.totalTimeSeconds"));
    }

    @Test
    void importDeductsGreenCoffeeFromStockAndLinksTheLot() {
        long lot = createLot("COL-1", "Colombia", 0);
        receive(lot, warehouse, 20).then().statusCode(201);

        var response = importRoast("medium.csv", csvRoast(MEDIUM_5KG),
                Map.of("name", "Huila test", "lots", List.of(lotWeight(lot, 5000))));

        response.then().statusCode(201);
        JsonPath roast = response.jsonPath();
        assertThat(roast.getString("summary.batchCode")).isEqualTo("PR-0001");
        assertThat(roast.getString("summary.name")).isEqualTo("Huila test");
        assertThat(roast.getDouble("summary.greenWeightG")).isEqualTo(5000.0);
        assertThat(roast.getDouble("summary.lossPercent")).isCloseTo(14.5, within(0.1));
        assertThat(roast.getString("summary.lots[0].code")).isEqualTo("COL-1");
        assertThat(roast.getString("sourceFilename")).isEqualTo("medium.csv");

        assertThat(stockOf(lot)).isCloseTo(15.0, within(1e-9));
        JsonPath ledger = given().get("/api/lots/{id}/movements", lot).jsonPath();
        assertThat(ledger.getString("[0].type")).isEqualTo("ROAST_CONSUMPTION");
        assertThat(ledger.getDouble("[0].quantityKg")).isCloseTo(-5.0, within(1e-9));
        assertThat(ledger.getLong("[0].roastId")).isEqualTo(roast.getLong("summary.id"));
    }

    @Test
    void anImportWithoutLotsDoesNotTouchStock() {
        long lot = createLot("COL-1", "Colombia", 0);
        receive(lot, warehouse, 20).then().statusCode(201);

        importRoast("medium.csv", csvRoast(MEDIUM_5KG), null).then().statusCode(201)
                .body("summary.lots.size()", equalTo(0));

        assertThat(stockOf(lot)).isCloseTo(20.0, within(1e-9));
    }

    @Test
    void aBlendConsumesEveryLotItIsMadeOf() {
        long a = createLot("BRA-1", "Brazil", 0);
        long b = createLot("COL-1", "Colombia", 0);
        receive(a, warehouse, 10).then().statusCode(201);
        receive(b, warehouse, 10).then().statusCode(201);

        importRoast("blend.csv", csvRoast(MEDIUM_5KG),
                Map.of("name", "House blend", "lots", List.of(lotWeight(a, 3000), lotWeight(b, 2000))))
                .then().statusCode(201).body("summary.lots.size()", equalTo(2));

        assertThat(stockOf(a)).isCloseTo(7.0, within(1e-9));
        assertThat(stockOf(b)).isCloseTo(8.0, within(1e-9));
    }

    @Test
    void deletingARoastPutsItsGreenCoffeeBackIntoStock() {
        long lot = createLot("COL-1", "Colombia", 0);
        receive(lot, warehouse, 20).then().statusCode(201);
        long roastId = importRoast("medium.csv", csvRoast(MEDIUM_5KG),
                Map.of("lots", List.of(lotWeight(lot, 5000)))).then().statusCode(201).extract().jsonPath()
                .getLong("summary.id");
        assertThat(stockOf(lot)).isCloseTo(15.0, within(1e-9));

        given().delete("/api/roasts/{id}", roastId).then().statusCode(204);

        assertThat(stockOf(lot)).isCloseTo(20.0, within(1e-9));
        assertThat(given().get("/api/lots/{id}/movements", lot).jsonPath().getList("type"))
                .containsExactly("ROAST_REVERSAL", "ROAST_CONSUMPTION", "RECEIPT");
        given().get("/api/roasts/{id}", roastId).then().statusCode(404);
        given().delete("/api/roasts/{id}", roastId).then().statusCode(404);
    }

    @Test
    void importFailsAndLeavesNoTraceWhenStockIsInsufficient() {
        long lot = createLot("COL-1", "Colombia", 0);
        receive(lot, warehouse, 3).then().statusCode(201);

        importRoast("medium.csv", csvRoast(MEDIUM_5KG), Map.of("lots", List.of(lotWeight(lot, 5000))))
                .then().statusCode(409).body("detail", containsString("Not enough stock"));

        given().get("/api/roasts").then().body("totalItems", equalTo(0));
        assertThat(given().get("/api/lots/{id}/movements", lot).jsonPath().getList("$")).hasSize(1);
        assertThat(stockOf(lot)).isCloseTo(3.0, within(1e-9));
    }

    @Test
    void concurrentRoastsCannotSpendTheSameKilogramsTwice() throws Exception {
        long lot = createLot("COL-1", "Colombia", 0);
        receive(lot, warehouse, 6).then().statusCode(201);
        String csv = csvRoast(MEDIUM_5KG);

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                int n = i;
                Callable<Integer> task = () -> importRoast("r" + n + ".csv", csv,
                        Map.of("lots", List.of(lotWeight(lot, 5000)))).statusCode();
                futures.add(pool.submit(task));
            }
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> f : futures) {
                codes.add(f.get());
            }
            assertThat(codes).containsOnly(201, 409);
            assertThat(codes.stream().filter(c -> c == 201).count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(stockOf(lot)).isCloseTo(1.0, within(1e-9));
        given().get("/api/roasts").then().body("totalItems", equalTo(1));
    }

    @Test
    void importChecksLotsAndWeights() {
        long lot = createLot("COL-1", "Colombia", 0);
        receive(lot, warehouse, 20).then().statusCode(201);
        String csv = csvRoast(MEDIUM_5KG);

        importRoast("a.csv", csv, Map.of("lots", List.of(lotWeight(9999, 5000)))).then().statusCode(404);
        importRoast("a.csv", csv, Map.of("profileId", 9999)).then().statusCode(404);
        importRoast("a.csv", csv, Map.of("lots", List.of(lotWeight(lot, 2500), lotWeight(lot, 2500))))
                .then().statusCode(400).body("detail", containsString("listed twice"));
        importRoast("a.csv", csv, Map.of("greenWeightG", 4000, "lots", List.of(lotWeight(lot, 5000))))
                .then().statusCode(400).body("detail", containsString("add up to"));
        importRoast("a.csv", csv, Map.of("roastedWeightG", 6000, "lots", List.of(lotWeight(lot, 5000))))
                .then().statusCode(422).body("title", equalTo("Inconsistent roast data"));

        assertThat(stockOf(lot)).isCloseTo(20.0, within(1e-9));
        given().get("/api/roasts").then().body("totalItems", equalTo(0));
    }

    @Test
    void rejectsUnreadableUploadsWithHelpfulMessages() {
        importRoast("empty.csv", "", null).then().statusCode(400).body("detail", containsString("empty"));
        importRoast("x.alog", "not a profile", null).then().statusCode(400)
                .body("title", equalTo("Could not read the roast log"))
                .body("detail", containsString("Not a valid Artisan profile"));

        StringBuilder bad = new StringBuilder("Time,BT\n");
        for (int t = 0; t < 20; t++) {
            bad.append(t).append(',').append(t == 2 ? "abc" : String.valueOf(100 + t)).append('\n');
        }
        JsonPath problem = importRoast("bad.csv", bad.toString(), null).then().statusCode(400).extract().jsonPath();
        assertThat(problem.getList("details", String.class)).containsExactly("Line 4: invalid bean temperature 'abc'");
    }

    @Test
    void rejectsInconsistentEventsWithAnExplanation() {
        StringBuilder csv = new StringBuilder("Time,BT,Event\n");
        for (int t = 0; t < 30; t++) {
            csv.append(t).append(',').append(100 + t).append(',')
                    .append(t == 10 ? "FC Start" : t == 20 ? "Dry End" : "").append('\n');
        }

        preview("events.csv", csv.toString()).then().statusCode(422)
                .body("detail", containsString("is before Dry end"));
    }

    @Test
    void theCommittedSampleFilesAreValid() throws IOException {
        Path samples = Path.of("..", "samples");
        List<Path> files;
        try (Stream<Path> list = Files.list(samples)) {
            files = list.filter(p -> p.toString().endsWith(".csv") || p.toString().endsWith(".alog")).toList();
        }
        assertThat(files).hasSizeGreaterThanOrEqualTo(4);
        for (Path file : files) {
            JsonPath p = preview(file.getFileName().toString(), Files.readString(file)).then().statusCode(200)
                    .extract().jsonPath();

            assertThat(p.getDouble("metrics.developmentTimeRatioPercent")).as(file.toString()).isBetween(10.0, 25.0);
            assertThat(p.getDouble("metrics.roastLossPercent")).as(file.toString()).isBetween(10.0, 20.0);
            assertThat(p.getString("title")).as(file.toString()).isNotBlank();
        }
    }

    @Test
    void importAcceptsTheRequestPartAsOptional() {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "Only a name");

        importRoast("a.csv", csvRoast(MEDIUM_5KG), request).then().statusCode(201)
                .body("summary.name", equalTo("Only a name"))
                .body("summary.machine", equalTo("Test machine"));
    }
}
