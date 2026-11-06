package dev.roastbench.inventory;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;

import dev.roastbench.AbstractIntegrationTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InventoryApiIT extends AbstractIntegrationTest {

    @Test
    void newLotStartsEmptyAndRaisesAnAlertWhenItHasAMinimumLevel() {
        long id = createLot("ETH-1", "Ethiopia", 10);

        given().get("/api/lots/{id}", id).then().statusCode(200)
                .body("code", equalTo("ETH-1"))
                .body("status", equalTo("OUT"))
                .body("archived", equalTo(false));
        assertThat(stockOf(id)).isZero();

        JsonPath alerts = given().get("/api/inventory/alerts").then().statusCode(200).extract().jsonPath();
        assertThat(alerts.getList("$")).hasSize(1);
        assertThat(alerts.getString("[0].severity")).isEqualTo("OUT");
        assertThat(alerts.getString("[0].code")).isEqualTo("ETH-1");
    }

    @Test
    void receiptsAdjustmentsAndTransfersKeepTheLedgerConsistent() {
        long lot = createLot("COL-1", "Colombia", 0);
        long a = createLocation("Warehouse");
        long b = createLocation("Shelf");

        receive(lot, a, 50).then().statusCode(201);
        given().contentType(ContentType.JSON)
                .body(Map.of("locationId", a, "deltaKg", -2.5, "reason", "Stock count"))
                .post("/api/lots/{id}/adjustments", lot).then().statusCode(201);
        given().contentType(ContentType.JSON)
                .body(Map.of("fromLocationId", a, "toLocationId", b, "quantityKg", 20))
                .post("/api/lots/{id}/transfers", lot).then().statusCode(201);

        assertThat(stockOf(lot)).isCloseTo(47.5, within(1e-9));

        JsonPath byLocation = given().get("/api/lots/{id}/stock", lot).then().statusCode(200).extract().jsonPath();
        assertThat(byLocation.getString("[0].locationName")).isEqualTo("Warehouse");
        assertThat(byLocation.getDouble("[0].stockKg")).isCloseTo(27.5, within(1e-9));
        assertThat(byLocation.getString("[1].locationName")).isEqualTo("Shelf");
        assertThat(byLocation.getDouble("[1].stockKg")).isCloseTo(20.0, within(1e-9));

        JsonPath ledger = given().get("/api/lots/{id}/movements", lot).then().statusCode(200).extract().jsonPath();
        assertThat(ledger.getList("type")).containsExactly("TRANSFER_IN", "TRANSFER_OUT", "ADJUSTMENT", "RECEIPT");
        assertThat(ledger.getList("balanceKg", Double.class)).containsExactly(47.5, 27.5, 47.5, 50.0);
        assertThat(ledger.getList("quantityKg", Double.class)).containsExactly(20.0, -20.0, -2.5, 50.0);
    }

    @Test
    void rejectsInvalidMovements() {
        long lot = createLot("KEN-1", "Kenya", 0);
        long a = createLocation("A");
        long b = createLocation("B");
        receive(lot, a, 10).then().statusCode(201);

        receive(lot, a, 0).then().statusCode(400).body("errors", hasKey("quantityKg"));
        given().contentType(ContentType.JSON).body(Map.of("locationId", a, "deltaKg", -1))
                .post("/api/lots/{id}/adjustments", lot).then().statusCode(400).body("errors", hasKey("reason"));
        given().contentType(ContentType.JSON).body(Map.of("locationId", a, "deltaKg", -11, "reason", "oops"))
                .post("/api/lots/{id}/adjustments", lot).then().statusCode(409)
                .body("detail", containsString("holds only 10 kg"));
        given().contentType(ContentType.JSON).body(Map.of("locationId", a, "deltaKg", 0, "reason", "nothing"))
                .post("/api/lots/{id}/adjustments", lot).then().statusCode(400);
        given().contentType(ContentType.JSON).body(Map.of("fromLocationId", a, "toLocationId", a, "quantityKg", 1))
                .post("/api/lots/{id}/transfers", lot).then().statusCode(400);
        given().contentType(ContentType.JSON).body(Map.of("fromLocationId", a, "toLocationId", b, "quantityKg", 11))
                .post("/api/lots/{id}/transfers", lot).then().statusCode(409)
                .body("detail", containsString("Not enough stock"));
        receive(lot, 9999, 1).then().statusCode(404);
        receive(9999, a, 1).then().statusCode(404);
        assertThat(stockOf(lot)).isCloseTo(10.0, within(1e-9));
    }

    @Test
    void lotCodesAreUniqueIgnoringCase() {
        createLot("abc-1", "Peru", 0);

        given().contentType(ContentType.JSON)
                .body(Map.of("code", "ABC-1", "name", "Other", "country", "Peru"))
                .post("/api/lots").then().statusCode(409).body("title", equalTo("Conflict"));
    }

    @Test
    void validatesLotFields() {
        given().contentType(ContentType.JSON).body(Map.of("code", "", "name", "x", "country", "y"))
                .post("/api/lots").then().statusCode(400).body("errors", hasKey("code"));
        given().contentType(ContentType.JSON)
                .body(Map.of("code", "X-1", "name", "x", "country", "y", "altitudeM", 99999))
                .post("/api/lots").then().statusCode(400).body("errors", hasKey("altitudeM"));
    }

    @Test
    void lotCanBeEdited() {
        long id = createLot("OLD-1", "Peru", 5);

        given().contentType(ContentType.JSON)
                .body(Map.of("code", "NEW-1", "name", "Renamed", "country", "Peru", "region", "Cajamarca",
                        "minStockKg", 8, "cropYear", 2025))
                .put("/api/lots/{id}", id).then().statusCode(200)
                .body("code", equalTo("NEW-1")).body("region", equalTo("Cajamarca")).body("cropYear", equalTo(2025));
    }

    @Test
    void alertAppearsBelowTheMinimumLevelAndGoesAwayAfterAReceipt() {
        long lot = createLot("GUA-1", "Guatemala", 20);
        long loc = createLocation("Warehouse");

        receive(lot, loc, 30).then().statusCode(201);
        assertThat(given().get("/api/inventory/alerts").jsonPath().getList("$")).isEmpty();

        given().contentType(ContentType.JSON).body(Map.of("locationId", loc, "deltaKg", -15, "reason", "Count"))
                .post("/api/lots/{id}/adjustments", lot).then().statusCode(201).body("status", equalTo("LOW"));
        JsonPath alerts = given().get("/api/inventory/alerts").jsonPath();
        assertThat(alerts.getList("$")).hasSize(1);
        assertThat(alerts.getString("[0].severity")).isEqualTo("LOW");
        assertThat(alerts.getDouble("[0].stockKg")).isCloseTo(15.0, within(1e-9));

        receive(lot, loc, 10).then().statusCode(201).body("status", equalTo("OK"));
        assertThat(given().get("/api/inventory/alerts").jsonPath().getList("$")).isEmpty();
    }

    @Test
    void mostUrgentAlertsComeFirst() {
        long low = createLot("LOW-1", "Brazil", 100);
        long out = createLot("OUT-1", "Peru", 5);
        long fine = createLot("FINE-1", "Kenya", 5);
        long loc = createLocation("Warehouse");
        receive(low, loc, 60).then().statusCode(201);
        receive(fine, loc, 50).then().statusCode(201);

        List<String> codes = given().get("/api/inventory/alerts").jsonPath().getList("code");

        assertThat(codes).containsExactly("OUT-1", "LOW-1");
        assertThat(out).isPositive();
    }

    @Test
    void archivedLotRefusesMovementsButKeepsItsHistory() {
        long lot = createLot("ARC-1", "Peru", 0);
        long loc = createLocation("Warehouse");
        receive(lot, loc, 5).then().statusCode(201);

        given().post("/api/lots/{id}/archive", lot).then().statusCode(200).body("archived", equalTo(true));
        receive(lot, loc, 1).then().statusCode(409).body("detail", containsString("archived"));
        assertThat(given().get("/api/lots").jsonPath().getList("$")).isEmpty();
        assertThat(given().get("/api/lots?includeArchived=true").jsonPath().getList("$")).hasSize(1);
        assertThat(given().get("/api/lots/{id}/movements", lot).jsonPath().getList("$")).hasSize(1);
        assertThat(given().get("/api/inventory/alerts").jsonPath().getList("$")).isEmpty();

        given().post("/api/lots/{id}/restore", lot).then().statusCode(200).body("archived", equalTo(false));
        receive(lot, loc, 1).then().statusCode(201);
    }

    @Test
    void summaryAddsUpStockValueAndCountries() {
        long a = createLot("A-1", "Ethiopia", 0, 10.0);
        long b = createLot("B-1", "Ethiopia", 100, 20.0);
        long c = createLot("C-1", "Brazil", 0, null);
        long loc = createLocation("Warehouse");
        receive(a, loc, 5).then().statusCode(201);
        receive(b, loc, 2).then().statusCode(201);
        receive(c, loc, 3).then().statusCode(201);

        JsonPath summary = given().get("/api/inventory/summary").then().statusCode(200).extract().jsonPath();

        assertThat(summary.getDouble("totalStockKg")).isCloseTo(10.0, within(1e-9));
        assertThat(summary.getDouble("stockValue")).isCloseTo(90.0, within(1e-9));
        assertThat(summary.getInt("lotCount")).isEqualTo(3);
        assertThat(summary.getInt("lowStockCount")).isEqualTo(1);
        assertThat(summary.getInt("outOfStockCount")).isZero();
        assertThat(summary.getString("byCountry[0].country")).isEqualTo("Ethiopia");
        assertThat(summary.getDouble("byCountry[0].stockKg")).isCloseTo(7.0, within(1e-9));
    }

    @Test
    void lotListFiltersBySearchCountryAndStatus() {
        long a = createLot("ETH-1", "Ethiopia", 10);
        long b = createLot("COL-1", "Colombia", 0);
        long loc = createLocation("Warehouse");
        receive(a, loc, 3).then().statusCode(201);
        receive(b, loc, 30).then().statusCode(201);

        assertThat(given().get("/api/lots?q=eth").jsonPath().getList("code")).containsExactly("ETH-1");
        assertThat(given().get("/api/lots?country=colombia").jsonPath().getList("code")).containsExactly("COL-1");
        assertThat(given().get("/api/lots?status=LOW").jsonPath().getList("code")).containsExactly("ETH-1");
        assertThat(given().get("/api/lots?status=OK").jsonPath().getList("code")).containsExactly("COL-1");
        assertThat(given().get("/api/lots").jsonPath().getList("code")).containsExactly("COL-1", "ETH-1");
    }

    @Test
    void locationNamesAreUnique() {
        createLocation("Warehouse");

        given().contentType(ContentType.JSON).body(Map.of("name", "warehouse"))
                .post("/api/locations").then().statusCode(409);
        assertThat(given().get("/api/locations").jsonPath().getList("name")).containsExactly("Warehouse");
    }

    @Test
    void unknownRoutesAndIdsGiveProblemDetails() {
        given().get("/api/lots/12345").then().statusCode(404).body("title", equalTo("Not found"));
        given().get("/api/nope").then().statusCode(404);
    }
}
