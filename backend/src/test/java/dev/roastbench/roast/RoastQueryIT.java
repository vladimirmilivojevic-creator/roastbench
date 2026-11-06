package dev.roastbench.roast;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import dev.roastbench.AbstractIntegrationTest;
import dev.roastbench.demo.SyntheticRoastGenerator.Spec;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoastQueryIT extends AbstractIntegrationTest {

    private long warehouse;
    private long lotA;
    private long lotB;
    private long profile;
    private long alpha;
    private long bravo;
    private long charlie;

    @BeforeEach
    void createThreeRoasts() {
        warehouse = createLocation("Warehouse");
        lotA = createLot("LOT-A", "Colombia", 0);
        lotB = createLot("LOT-B", "Ethiopia", 0);
        receive(lotA, warehouse, 100).then().statusCode(201);
        receive(lotB, warehouse, 100).then().statusCode(201);
        profile = createProfile("Everyday");
        alpha = importWith("Alpha medium", Spec.medium(1).withGreenWeightG(5000), "2026-10-01T09:00:00Z", profile, lotA);
        bravo = importWith("Bravo light", Spec.light(2).withGreenWeightG(3000), "2026-10-05T09:00:00Z", profile, lotB);
        charlie = importWith("Charlie dark", Spec.dark(3).withGreenWeightG(5000), "2026-10-09T09:00:00Z", null, lotA);
    }

    private long importWith(String name, Spec spec, String roastedAt, Long profileId, Long lotId) {
        Map<String, Object> request = new HashMap<>();
        request.put("name", name);
        request.put("roastedAt", roastedAt);
        if (profileId != null) {
            request.put("profileId", profileId);
        }
        request.put("lots", List.of(lotWeight(lotId, (int) spec.greenWeightG())));
        return importRoast(name + ".csv", csvRoast(spec), request).then().statusCode(201).extract().jsonPath()
                .getLong("summary.id");
    }

    private List<String> names(String query) {
        return given().get("/api/roasts" + query).then().statusCode(200).extract().jsonPath().getList("items.name");
    }

    @Test
    void listsNewestFirstByDefault() {
        assertThat(names("")).containsExactly("Charlie dark", "Bravo light", "Alpha medium");
        assertThat(names("?direction=asc")).containsExactly("Alpha medium", "Bravo light", "Charlie dark");
    }

    @Test
    void sortsByDevelopmentTimeRatioLossAndName() {
        assertThat(names("?sort=dtr&direction=asc")).containsExactly("Bravo light", "Alpha medium", "Charlie dark");
        assertThat(names("?sort=dtr&direction=desc")).containsExactly("Charlie dark", "Alpha medium", "Bravo light");
        assertThat(names("?sort=loss&direction=asc")).containsExactly("Bravo light", "Alpha medium", "Charlie dark");
        assertThat(names("?sort=name&direction=asc")).containsExactly("Alpha medium", "Bravo light", "Charlie dark");
        assertThat(names("?sort=totalTime&direction=asc")).hasSize(3);
    }

    @Test
    void filtersBySearchProfileLotAndDate() {
        assertThat(names("?q=bravo")).containsExactly("Bravo light");
        assertThat(names("?q=test machine")).hasSize(3);
        assertThat(names("?profileId=" + profile)).containsExactly("Bravo light", "Alpha medium");
        assertThat(names("?lotId=" + lotA)).containsExactly("Charlie dark", "Alpha medium");
        assertThat(names("?from=2026-10-04T00:00:00Z&to=2026-10-08T00:00:00Z")).containsExactly("Bravo light");
        assertThat(names("?q=nothing-like-this")).isEmpty();
    }

    @Test
    void pagesResults() {
        JsonPath page = given().get("/api/roasts?size=2&page=1").then().statusCode(200).extract().jsonPath();

        assertThat(page.getList("items")).hasSize(1);
        assertThat(page.getInt("totalItems")).isEqualTo(3);
        assertThat(page.getInt("totalPages")).isEqualTo(2);
        assertThat(page.getInt("page")).isEqualTo(1);
    }

    @Test
    void rejectsUnknownSortFields() {
        given().get("/api/roasts?sort=password").then().statusCode(400).body("detail", containsString("Cannot sort"));
    }

    @Test
    void listRowsCarryLotsProfileAndBatchCode() {
        JsonPath list = given().get("/api/roasts?sort=batchNo&direction=asc").jsonPath();

        assertThat(list.getList("items.batchCode")).containsExactly("PR-0001", "PR-0002", "PR-0003");
        assertThat(list.getString("items[0].profile.name")).isEqualTo("Everyday");
        assertThat(list.getString("items[0].lots[0].code")).isEqualTo("LOT-A");
        assertThat(list.getString("items[2].profile")).isNull();
    }

    @Test
    void detailExposesEventsPhasesAndMetrics() {
        JsonPath d = given().get("/api/roasts/{id}", alpha).then().statusCode(200).extract().jsonPath();

        assertThat(d.getString("summary.sourceFormat")).isEqualTo("CSV");
        assertThat(d.getDouble("summary.dtrPercent")).isEqualTo(d.getDouble("metrics.developmentTimeRatioPercent"));
        assertThat(d.getList("metrics.phases.phase")).containsExactly("DRYING", "MAILLARD", "DEVELOPMENT");
        double share = d.getList("metrics.phases.percentOfTotal", Double.class).stream()
                .mapToDouble(Double::doubleValue).sum();
        assertThat(share).isCloseTo(100.0, within(1e-6));

        List<String> types = d.getList("events.type");
        assertThat(types.get(0)).isEqualTo("CHARGE");
        assertThat(types).contains("TURNING_POINT", "DRY_END", "FIRST_CRACK_START", "DROP");
        int tp = types.indexOf("TURNING_POINT");
        int dry = types.indexOf("DRY_END");
        assertThat(d.getBoolean("events[" + tp + "].estimated")).isTrue();
        assertThat(d.getBoolean("events[" + dry + "].estimated")).isFalse();
        List<Double> times = d.getList("events.timeSeconds", Double.class);
        assertThat(times).isSorted();
    }

    @Test
    void curveHasARateOfRiseForTheRequestedWindow() {
        JsonPath c = given().get("/api/roasts/{id}/curve", alpha).then().statusCode(200).extract().jsonPath();

        assertThat(c.getDouble("rorWindowSeconds")).isEqualTo(30.0);
        List<Double> ror = c.getList("ror", Double.class);
        assertThat(ror).hasSameSizeAs(c.getList("timeSeconds"));
        assertThat(ror.get(ror.size() / 2)).isBetween(3.0, 25.0);
        assertThat(c.getList("envTemp")).hasSameSizeAs(c.getList("beanTemp"));

        assertThat(given().get("/api/roasts/{id}/curve?rorWindowSeconds=60", alpha).jsonPath()
                .getDouble("rorWindowSeconds")).isEqualTo(60.0);
        given().get("/api/roasts/{id}/curve?rorWindowSeconds=1", alpha).then().statusCode(400)
                .body("detail", containsString("RoR window"));
        given().get("/api/roasts/{id}/curve", 9999).then().statusCode(404);
    }

    @Test
    void comparisonUsesTheReferenceRoastOfTheProfile() {
        given().contentType(ContentType.JSON).body(Map.of("roastId", alpha))
                .put("/api/profiles/{id}/reference", profile).then().statusCode(200)
                .body("referenceRoast.id", equalTo((int) alpha)).body("referenceRoast.batchCode", equalTo("PR-0001"));

        JsonPath cmp = given().get("/api/roasts/{id}/comparison", bravo).then().statusCode(200).extract().jsonPath();

        assertThat(cmp.getLong("referenceId")).isEqualTo(alpha);
        assertThat(cmp.getDouble("result.maxAbsDeviation")).isGreaterThan(3.0);
        assertThat(cmp.getDouble("result.rmse")).isPositive();
        assertThat(cmp.getDouble("result.dtrDeltaPercentPoints")).isNegative();
        assertThat(cmp.getList("result.gridSeconds")).hasSameSizeAs(cmp.getList("result.beanTempDelta"));
        assertThat(cmp.getList("result.phases.phase")).containsExactly("DRYING", "MAILLARD", "DEVELOPMENT");

        JsonPath self = given().get("/api/roasts/{id}/comparison", alpha).jsonPath();
        assertThat(self.getDouble("result.rmse")).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void comparisonNeedsAReferenceButAcceptsAnExplicitOne() {
        given().get("/api/roasts/{id}/comparison", charlie).then().statusCode(409)
                .body("detail", containsString("no reference roast"));
        given().get("/api/roasts/{id}/comparison?referenceId={ref}", charlie, alpha).then().statusCode(200)
                .body("referenceId", equalTo((int) alpha));
        given().get("/api/roasts/{id}/comparison?referenceId=9999", charlie).then().statusCode(404);
    }

    @Test
    void onlyARoastOfTheProfileCanBeItsReference() {
        given().contentType(ContentType.JSON).body(Map.of("roastId", charlie))
                .put("/api/profiles/{id}/reference", profile).then().statusCode(400)
                .body("detail", containsString("does not belong"));

        given().contentType(ContentType.JSON).body(Map.of("profileId", profile))
                .put("/api/roasts/{id}/profile", charlie).then().statusCode(200)
                .body("summary.profile.name", equalTo("Everyday"));
        given().contentType(ContentType.JSON).body(Map.of("roastId", charlie))
                .put("/api/profiles/{id}/reference", profile).then().statusCode(200);
        given().get("/api/roasts/{id}", charlie).then().body("summary.referenceOfProfile", equalTo(true));

        given().contentType(ContentType.JSON).body(new HashMap<String, Object>() {{
            put("profileId", null);
        }}).put("/api/roasts/{id}/profile", charlie).then().statusCode(200).body("summary.profile", nullValue());
        given().get("/api/profiles/{id}", profile).then().body("referenceRoast", nullValue());
    }

    @Test
    void deletingTheReferenceRoastClearsTheProfileReference() {
        given().contentType(ContentType.JSON).body(Map.of("roastId", alpha))
                .put("/api/profiles/{id}/reference", profile).then().statusCode(200);

        given().delete("/api/roasts/{id}", alpha).then().statusCode(204);

        given().get("/api/profiles/{id}", profile).then().statusCode(200).body("referenceRoast", nullValue())
                .body("roastCount", equalTo(1));
    }

    @Test
    void profileListShowsRoastCountAndAverages() {
        double dtrAlpha = given().get("/api/roasts/{id}", alpha).jsonPath().getDouble("summary.dtrPercent");
        double dtrBravo = given().get("/api/roasts/{id}", bravo).jsonPath().getDouble("summary.dtrPercent");

        JsonPath profiles = given().get("/api/profiles").then().statusCode(200).extract().jsonPath();

        assertThat(profiles.getString("[0].name")).isEqualTo("Everyday");
        assertThat(profiles.getLong("[0].roastCount")).isEqualTo(2);
        assertThat(profiles.getDouble("[0].avgDtrPercent")).isCloseTo((dtrAlpha + dtrBravo) / 2, within(1e-6));
    }

    @Test
    void profileNamesAreUniqueAndValidated() {
        given().contentType(ContentType.JSON).body(Map.of("name", "everyday"))
                .post("/api/profiles").then().statusCode(409);
        given().contentType(ContentType.JSON).body(Map.of("name", ""))
                .post("/api/profiles").then().statusCode(400);
        long other = createProfile("Other");
        given().contentType(ContentType.JSON).body(Map.of("name", "Everyday"))
                .put("/api/profiles/{id}", other).then().statusCode(409);
        given().delete("/api/profiles/{id}", other).then().statusCode(204);
        given().get("/api/profiles/{id}", other).then().statusCode(404);
    }

    @Test
    void deletingAProfileKeepsItsRoasts() {
        given().delete("/api/profiles/{id}", profile).then().statusCode(204);

        assertThat(names("")).hasSize(3);
        given().get("/api/roasts/{id}", alpha).then().body("summary.profile", nullValue());
    }

    @Test
    void compareReturnsCurvesAndMetricsOfSeveralRoasts() {
        JsonPath c = given().get("/api/roasts/compare?ids={a},{b},{c}", alpha, bravo, charlie).then().statusCode(200)
                .extract().jsonPath();

        assertThat(c.getList("items")).hasSize(3);
        assertThat(c.getList("items.roast.name")).containsExactly("Alpha medium", "Bravo light", "Charlie dark");
        assertThat(c.getList("items[0].curve.timeSeconds")).isNotEmpty();
        assertThat(c.getDouble("items[2].metrics.developmentTimeRatioPercent")).isGreaterThan(c.getDouble(
                "items[1].metrics.developmentTimeRatioPercent"));

        given().get("/api/roasts/compare?ids=" + alpha + "," + alpha).then().statusCode(200)
                .body("items.size()", equalTo(1));
    }

    @Test
    void compareAcceptsAtMostTenRoasts() {
        List<Long> eleven = new ArrayList<>();
        for (int i = 1; i <= 11; i++) {
            eleven.add((long) i);
        }

        given().get("/api/roasts/compare?ids=" + String.join(",", eleven.stream().map(String::valueOf).toList()))
                .then().statusCode(400).body("detail", containsString("between 1 and 10"));
        given().get("/api/roasts/compare?ids=" + alpha + ",9999").then().statusCode(404);
    }

    @Test
    void editingEventsRecalculatesTheMetrics() {
        JsonPath before = given().get("/api/roasts/{id}", alpha).jsonPath();
        double total = before.getDouble("metrics.totalTimeSeconds");

        JsonPath after = given().contentType(ContentType.JSON)
                .body(Map.of("events", Map.of("FIRST_CRACK_START", 450.0)))
                .patch("/api/roasts/{id}", alpha).then().statusCode(200).extract().jsonPath();

        assertThat(after.getDouble("summary.firstCrackSeconds")).isEqualTo(450.0);
        assertThat(after.getDouble("summary.dtrPercent")).isCloseTo((total - 450.0) / total * 100.0, within(1e-6));
        assertThat(after.getDouble("summary.developmentSeconds")).isCloseTo(total - 450.0, within(1e-6));
    }

    @Test
    void removingFirstCrackRemovesTheDevelopmentPhase() {
        Map<String, Object> events = new HashMap<>();
        events.put("FIRST_CRACK_START", null);
        events.put("FIRST_CRACK_END", null);

        JsonPath after = given().contentType(ContentType.JSON).body(Map.of("events", events))
                .patch("/api/roasts/{id}", alpha).then().statusCode(200).extract().jsonPath();

        assertThat(after.getString("summary.dtrPercent")).isNull();
        assertThat(after.getList("metrics.phases.phase")).containsExactly("DRYING");
    }

    @Test
    void anInvalidEventEditIsRejectedAndChangesNothing() {
        double dtr = given().get("/api/roasts/{id}", alpha).jsonPath().getDouble("summary.dtrPercent");

        given().contentType(ContentType.JSON).body(Map.of("events", Map.of("DRY_END", 99999.0)))
                .patch("/api/roasts/{id}", alpha).then().statusCode(422);
        given().contentType(ContentType.JSON).body(Map.of("events", Map.of("CHARGE", 5.0)))
                .patch("/api/roasts/{id}", alpha).then().statusCode(400).body("detail", containsString("cannot be edited"));
        given().contentType(ContentType.JSON).body("{\"events\": {\"NOPE\": 1}}")
                .patch("/api/roasts/{id}", alpha).then().statusCode(400);

        assertThat(given().get("/api/roasts/{id}", alpha).jsonPath().getDouble("summary.dtrPercent")).isEqualTo(dtr);
    }

    @Test
    void editingNameNotesAndRoastedWeightUpdatesLoss() {
        JsonPath after = given().contentType(ContentType.JSON)
                .body(Map.of("name", "Renamed", "notes", "Sweet, clean", "roastedWeightG", 4200))
                .patch("/api/roasts/{id}", alpha).then().statusCode(200).extract().jsonPath();

        assertThat(after.getString("summary.name")).isEqualTo("Renamed");
        assertThat(after.getString("notes")).isEqualTo("Sweet, clean");
        assertThat(after.getDouble("summary.lossPercent")).isCloseTo(16.0, within(1e-9));

        given().contentType(ContentType.JSON).body(Map.of("roastedWeightG", 6000))
                .patch("/api/roasts/{id}", alpha).then().statusCode(422);
        given().contentType(ContentType.JSON).body(Map.of("name", ""))
                .patch("/api/roasts/{id}", alpha).then().statusCode(400);
    }

    @Test
    void lotShowsAverageDailyUseAndDaysOfStockLeftFromRecentRoasts() {
        long lot = createLot("PROJ-1", "Peru", 0);
        receive(lot, warehouse, 30).then().statusCode(201);
        importWith("Ten days ago", Spec.medium(7).withGreenWeightG(5000),
                Instant.now().minus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString(), null, lot);

        JsonPath l = given().get("/api/lots/{id}", lot).jsonPath();

        // 5 kg over a 30 day window = 0.167 kg/day; 25 kg left lasts about 150 days
        assertThat(l.getDouble("avgDailyUseKg")).isCloseTo(5.0 / 30.0, within(0.001));
        assertThat(l.getLong("daysOfStockLeft")).isBetween(145L, 155L);
    }
}
