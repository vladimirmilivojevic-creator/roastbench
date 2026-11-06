package dev.roastbench.demo;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.roastbench.AbstractIntegrationTest;
import io.restassured.path.json.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;

class DemoDataIT extends AbstractIntegrationTest {

    private static void reset() {
        given().header("X-Admin-Token", "test-admin-token").post("/api/admin/demo/reset").then().statusCode(200);
    }

    @Test
    void resetNeedsTheAdminToken() {
        given().post("/api/admin/demo/reset").then().statusCode(403);
        given().header("X-Admin-Token", "wrong").post("/api/admin/demo/reset").then().statusCode(403);
        given().header("X-Admin-Token", "test-admin-token").post("/api/admin/demo/reset").then().statusCode(200);
    }

    @Test
    void seedBuildsAConsistentSyntheticRoastery() {
        reset();

        List<String> lotCodes = given().get("/api/lots").jsonPath().getList("code");
        assertThat(lotCodes).hasSize(7).contains("ETH-GUJI-25", "KEN-NYERI-25", "SUM-MAND-25");

        JsonPath roasts = given().get("/api/roasts?size=100&sort=batchNo&direction=asc").jsonPath();
        assertThat(roasts.getInt("totalItems")).isEqualTo(22);
        assertThat(roasts.getString("items[0].batchCode")).isEqualTo("PR-0001");
        assertThat(roasts.getList("items.dtrPercent", Double.class)).allSatisfy(d -> assertThat(d).isBetween(8.0, 28.0));
        assertThat(roasts.getList("items.lossPercent", Double.class)).allSatisfy(l -> assertThat(l).isBetween(10.0, 20.0));
        assertThat(roasts.getList("items.sourceFormat")).containsOnly("DEMO");

        JsonPath profiles = given().get("/api/profiles").jsonPath();
        assertThat(profiles.getList("name")).containsExactly("Espresso dark blend", "Filter light",
                "Medium all-rounder");
        assertThat(profiles.getList("roastCount", Long.class)).containsExactly(6L, 6L, 10L);
        assertThat(profiles.getList("referenceRoast.id")).doesNotContainNull();
    }

    @Test
    void seededStockMatchesTheLedgerAndRaisesTheExpectedAlerts() {
        reset();

        assertThat(stockByCode("ETH-GUJI-25")).isCloseTo(47.4, within(1e-9));   // 60 - 0.6 adjustment - 4 x 3 kg
        assertThat(stockByCode("BRA-CERR-25")).isCloseTo(162.0, within(1e-9));  // 150 + 30 - 6 x 3 kg
        assertThat(stockByCode("COL-HUILA-25")).isCloseTo(93.0, within(1e-9));  // 120 - 3 x 5 kg - 6 x 2 kg
        assertThat(stockByCode("SUM-MAND-25")).isZero();

        JsonPath alerts = given().get("/api/inventory/alerts").jsonPath();
        assertThat(alerts.getList("code")).containsExactly("SUM-MAND-25", "RWA-NYAM-25", "KEN-NYERI-25");
        assertThat(alerts.getList("severity")).containsExactly("OUT", "LOW", "LOW");
    }

    @Test
    void everySeededProfileHasAReferenceThatCanBeComparedAgainst() {
        reset();
        JsonPath profiles = given().get("/api/profiles").jsonPath();

        for (int i = 0; i < 3; i++) {
            long profileId = profiles.getLong("[" + i + "].id");
            List<Long> roastIds = given().get("/api/roasts?size=100&profileId=" + profileId).jsonPath()
                    .getList("items.id", Long.class);
            given().get("/api/roasts/{id}/comparison", roastIds.get(0)).then().statusCode(200);
        }
    }

    @Test
    void dashboardSummarisesTheSeededData() {
        reset();

        JsonPath d = given().get("/api/dashboard").then().statusCode(200).extract().jsonPath();

        assertThat(d.getInt("totalRoasts")).isEqualTo(22);
        assertThat(d.getInt("roastsLast30Days")).isBetween(10, 22);
        assertThat(d.getDouble("avgDtrPercentLast30Days")).isBetween(8.0, 28.0);
        assertThat(d.getDouble("kgRoastedLast30Days")).isPositive();
        assertThat(d.getList("recentRoasts")).hasSize(5);
        assertThat(d.getList("alerts")).hasSize(3);
        assertThat(d.getInt("inventory.lotCount")).isEqualTo(7);
        assertThat(d.getInt("inventory.outOfStockCount")).isEqualTo(1);
    }

    @Test
    void resettingTwiceGivesTheSameDemo() {
        reset();
        reset();

        JsonPath roasts = given().get("/api/roasts?size=100&sort=batchNo&direction=asc").jsonPath();
        assertThat(roasts.getInt("totalItems")).isEqualTo(22);
        assertThat(roasts.getString("items[0].batchCode")).isEqualTo("PR-0001");
        assertThat(given().get("/api/lots").jsonPath().getList("code")).hasSize(7);
    }

    private double stockByCode(String code) {
        return given().get("/api/lots?q=" + code).jsonPath().getDouble("[0].stockKg");
    }
}
