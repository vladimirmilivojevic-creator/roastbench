package dev.roastbench.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.roastbench.analysis.EventType;
import dev.roastbench.analysis.RoastCurve;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ArtisanAlogParserTest {

    private final ArtisanAlogParser parser = new ArtisanAlogParser();

    private static final String META = "'title': 'Guji natural', 'beans': 'Ethiopia Guji', 'roastertype': 'Probat 1kg',"
            + " 'roastisodate': '2026-03-14', 'roasttime': '09:30:00', 'weight': [250.0, 212.5, 'g'],"
            + " 'computed': {'DROP_time': 944.0, 'CHARGE_BT': 100.0}";

    @Test
    void readsMetadataWeightsAndTimestamp() {
        ParsedRoast roast = parser.parse(Fixtures.standardAlog(META));

        assertThat(roast.title()).isEqualTo("Guji natural");
        assertThat(roast.beans()).isEqualTo("Ethiopia Guji");
        assertThat(roast.machine()).isEqualTo("Probat 1kg");
        assertThat(roast.roastedAt()).isEqualTo(Instant.parse("2026-03-14T09:30:00Z"));
        assertThat(roast.greenWeightG()).isEqualTo(250.0);
        assertThat(roast.roastedWeightG()).isEqualTo(212.5);
        assertThat(roast.format()).isEqualTo(SourceFormat.ARTISAN_ALOG);
    }

    @Test
    void makesTimeRelativeToChargeAndDropsPreChargeSamples() {
        ParsedRoast roast = parser.parse(Fixtures.standardAlog(META));

        RoastCurve curve = roast.curve();
        assertThat(curve.size()).isEqualTo(601 - 4);
        assertThat(curve.firstTime()).isEqualTo(0.0);
        assertThat(curve.timeSeconds()[1]).isEqualTo(2.0);
        assertThat(curve.beanTemp()[0]).isCloseTo(100.0, within(1e-9));
        assertThat(curve.hasEnvTemp()).isTrue();
        assertThat(curve.envTemp()[0]).isEqualTo(250.0);
    }

    @Test
    void mapsTimeIndexToEventsRelativeToCharge() {
        ParsedRoast roast = parser.parse(Fixtures.standardAlog(META));

        assertThat(roast.events())
                .containsEntry(EventType.DRY_END, 192.0)
                .containsEntry(EventType.FIRST_CRACK_START, 592.0)
                .containsEntry(EventType.FIRST_CRACK_END, 652.0)
                .containsEntry(EventType.DROP, 952.0)
                .containsEntry(EventType.COOL, 1192.0)
                .doesNotContainKeys(EventType.SECOND_CRACK_START, EventType.SECOND_CRACK_END, EventType.CHARGE);
    }

    @Test
    void agreesWithArtisanComputedValuesWhenPresent() {
        // Artisan stores DROP_time as seconds since CHARGE in its own 'computed' block.
        double artisanDropTime = 952.0;
        String extra = "'computed': {'DROP_time': " + artisanDropTime + "}";

        ParsedRoast roast = parser.parse(Fixtures.standardAlog(extra));

        assertThat(roast.events().get(EventType.DROP)).isEqualTo(artisanDropTime);
    }

    @Test
    void convertsFahrenheitToCelsius() {
        double[] timex = Fixtures.series(20, i -> i);
        double[] bt = Fixtures.series(20, i -> 212.0); // 100 C
        double[] et = Fixtures.series(20, i -> 392.0); // 200 C
        String text = Fixtures.alog("F", timex, et, bt, new int[] {0, 0, 0, 0, 0, 0, 0, 0}, "");

        ParsedRoast roast = parser.parse(text);

        assertThat(roast.curve().beanTemp()[5]).isCloseTo(100.0, within(1e-9));
        assertThat(roast.curve().envTemp()[5]).isCloseTo(200.0, within(1e-9));
        assertThat(roast.warnings()).anyMatch(w -> w.contains("Fahrenheit"));
    }

    @Test
    void skipsSamplesWithoutBeanReadingAndInterpolatesEnvironmentGaps() {
        double[] timex = Fixtures.series(10, i -> i);
        double[] bt = Fixtures.series(10, i -> i == 3 ? -1.0 : 100.0 + i);
        double[] et = Fixtures.series(10, i -> i == 5 || i == 6 ? -1.0 : 200.0 + i);
        String text = Fixtures.alog("C", timex, et, bt, new int[] {0, 0, 0, 0, 0, 0, 0, 0}, "");

        ParsedRoast roast = parser.parse(text);

        assertThat(roast.curve().size()).isEqualTo(9);
        assertThat(roast.warnings()).anyMatch(w -> w.contains("1 samples without a bean temperature"));
        // env gap at t = 5, 6 is filled between 204 (t = 4) and 207 (t = 7)
        double[] t = roast.curve().timeSeconds();
        double[] env = roast.curve().envTemp();
        for (int i = 0; i < t.length; i++) {
            if (t[i] == 5.0) {
                assertThat(env[i]).isCloseTo(205.0, within(1e-9));
            }
            if (t[i] == 6.0) {
                assertThat(env[i]).isCloseTo(206.0, within(1e-9));
            }
        }
    }

    @Test
    void ignoresAnEnvironmentChannelThatHasNoReadings() {
        double[] timex = Fixtures.series(10, i -> i);
        double[] bt = Fixtures.series(10, i -> 100.0 + i);
        double[] et = Fixtures.series(10, i -> -1.0);
        String text = Fixtures.alog("C", timex, et, bt, new int[] {0, 0, 0, 0, 0, 0, 0, 0}, "");

        ParsedRoast roast = parser.parse(text);

        assertThat(roast.curve().hasEnvTemp()).isFalse();
    }

    @Test
    void usesFirstSampleWhenChargeIsNotMarked() {
        double[] timex = Fixtures.series(10, i -> 5.0 + i);
        double[] bt = Fixtures.series(10, i -> 100.0 + i);
        String text = Fixtures.alog("C", timex, bt, bt, new int[] {-1, 0, 0, 0, 0, 0, 0, 0}, "");

        ParsedRoast roast = parser.parse(text);

        assertThat(roast.curve().firstTime()).isEqualTo(0.0);
        assertThat(roast.curve().size()).isEqualTo(10);
        assertThat(roast.warnings()).anyMatch(w -> w.contains("CHARGE is not marked"));
    }

    @Test
    void convertsWeightUnits() {
        ParsedRoast kg = parser.parse(Fixtures.standardAlog("'weight': [0.25, 0.2125, 'Kg']"));
        ParsedRoast lb = parser.parse(Fixtures.standardAlog("'weight': [1.0, 0.85, 'lb']"));

        assertThat(kg.greenWeightG()).isCloseTo(250.0, within(1e-9));
        assertThat(kg.roastedWeightG()).isCloseTo(212.5, within(1e-9));
        assertThat(lb.greenWeightG()).isCloseTo(453.59237, within(1e-6));
    }

    @Test
    void ignoresWeightsThatContradictEachOther() {
        ParsedRoast roast = parser.parse(Fixtures.standardAlog("'weight': [200.0, 250.0, 'g']"));

        assertThat(roast.greenWeightG()).isNull();
        assertThat(roast.roastedWeightG()).isNull();
        assertThat(roast.warnings()).anyMatch(w -> w.contains("weights were ignored"));
    }

    @Test
    void treatsZeroWeightAsMissing() {
        ParsedRoast roast = parser.parse(Fixtures.standardAlog("'weight': [250.0, 0.0, 'g']"));

        assertThat(roast.greenWeightG()).isEqualTo(250.0);
        assertThat(roast.roastedWeightG()).isNull();
    }

    @Test
    void readsJsonProfilesToo() {
        String json = Fixtures.standardAlog(META).replace('\'', '"').replace("False", "false").replace("None", "null");

        ParsedRoast roast = parser.parse(json);

        assertThat(roast.title()).isEqualTo("Guji natural");
        assertThat(roast.events()).containsEntry(EventType.DROP, 952.0);
    }

    @Test
    void rejectsFilesThatAreNotArtisanProfiles() {
        assertThatThrownBy(() -> parser.parse("[1, 2, 3]"))
                .isInstanceOf(ImportException.class)
                .hasMessageContaining("not a dictionary");
        assertThatThrownBy(() -> parser.parse("{'title': 'x', 'temp2': [1, 2]}"))
                .isInstanceOf(ImportException.class)
                .hasMessageContaining("'timex' is missing");
        assertThatThrownBy(() -> parser.parse("{'timex': [1, 2, 3], 'temp2': [1, 2]}"))
                .isInstanceOf(ImportException.class)
                .hasMessageContaining("different lengths");
        assertThatThrownBy(() -> parser.parse("not a profile")).isInstanceOf(ImportException.class);
    }

    @Test
    void rejectsProfilesWithTooFewValidSamples() {
        String text = Fixtures.alog("C", new double[] {0, 1, 2}, new double[] {-1, -1, -1}, new double[] {-1, -1, 90},
                new int[] {0, 0, 0, 0, 0, 0, 0, 0}, "");

        assertThatThrownBy(() -> parser.parse(text))
                .isInstanceOf(ImportException.class)
                .hasMessageContaining("fewer than two valid");
    }
}
