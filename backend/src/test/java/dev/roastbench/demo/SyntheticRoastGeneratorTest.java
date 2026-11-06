package dev.roastbench.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.roastbench.analysis.AnalysisInput;
import dev.roastbench.analysis.CurveAnalyzer;
import dev.roastbench.analysis.EventType;
import dev.roastbench.analysis.PhaseMetrics.Phase;
import dev.roastbench.analysis.RoastMetrics;
import dev.roastbench.analysis.RorCalculator;
import dev.roastbench.demo.SampleFormats.Meta;
import dev.roastbench.demo.SyntheticRoastGenerator.GeneratedRoast;
import dev.roastbench.demo.SyntheticRoastGenerator.Spec;
import dev.roastbench.imports.CsvRoastParser;
import dev.roastbench.imports.ParsedRoast;
import dev.roastbench.imports.RoastLogImporter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SyntheticRoastGeneratorTest {

    private static final Meta META = new Meta("Test roast", "Test beans", "Test machine",
            Instant.parse("2026-10-12T08:30:00Z"));

    private static RoastMetrics analyze(GeneratedRoast roast) {
        return CurveAnalyzer.analyze(new AnalysisInput(roast.curve(), roast.events(), roast.greenWeightG(),
                roast.roastedWeightG()));
    }

    @Test
    void isDeterministicForTheSameSeedAndDiffersForAnotherSeed() {
        GeneratedRoast a = SyntheticRoastGenerator.generate(Spec.medium(7));
        GeneratedRoast b = SyntheticRoastGenerator.generate(Spec.medium(7));
        GeneratedRoast c = SyntheticRoastGenerator.generate(Spec.medium(8));

        assertThat(a.curve()).isEqualTo(b.curve());
        assertThat(a.curve()).isNotEqualTo(c.curve());
    }

    @Test
    void lightMediumAndDarkRoastsLookLikeRealDrumRoasts() {
        Object[][] cases = {
                {Spec.light(1), 10.0, 16.0, 200.0, 212.0},
                {Spec.medium(2), 13.0, 20.0, 204.0, 216.0},
                {Spec.dark(3), 17.0, 25.0, 208.0, 222.0}};

        for (Object[] c : cases) {
            Spec spec = (Spec) c[0];
            GeneratedRoast roast = SyntheticRoastGenerator.generate(spec);
            RoastMetrics m = analyze(roast);

            assertThat(m.turningPointSeconds()).as("turning point time").isBetween(35.0, 90.0);
            assertThat(m.turningPointBeanTemp()).as("turning point temp").isBetween(88.0, 112.0);
            assertThat(m.eventTime(EventType.DRY_END)).as("dry end").isBetween(200.0, 330.0);
            assertThat(m.eventTime(EventType.FIRST_CRACK_START)).as("first crack").isBetween(400.0, 650.0);
            assertThat(m.totalTimeSeconds()).as("total time").isBetween(520.0, 720.0);
            assertThat(m.developmentTimeRatioPercent()).as("DTR").isBetween((Double) c[1], (Double) c[2]);
            assertThat(m.dropBeanTemp()).as("drop temp").isBetween((Double) c[3], (Double) c[4]);
            assertThat(m.roastLossPercent()).isCloseTo(spec.lossPercent(), within(0.1));
            assertThat(m.phase(Phase.DRYING).percentOfTotal()).as("drying share").isBetween(35.0, 55.0);
        }
    }

    @Test
    void rateOfRiseDeclinesThroughTheRoastLikeInRealLogs() {
        GeneratedRoast roast = SyntheticRoastGenerator.generate(Spec.medium(5));
        RoastMetrics m = analyze(roast);
        double[] t = roast.curve().timeSeconds();
        double[] ror = RorCalculator.compute(t, roast.curve().beanTemp(), 30);

        int afterTp = indexAt(t, m.turningPointSeconds() + 60);
        int atCrack = indexAt(t, m.eventTime(EventType.FIRST_CRACK_START));
        int atDrop = indexAt(t, m.totalTimeSeconds());

        assertThat(ror[afterTp]).as("RoR a minute after TP").isBetween(10.0, 24.0);
        assertThat(ror[atCrack]).as("RoR at first crack").isBetween(4.0, 11.0);
        assertThat(ror[atDrop]).as("RoR at drop").isBetween(2.0, 9.0);
        assertThat(ror[afterTp]).isGreaterThan(ror[atCrack]);
        assertThat(ror[atCrack]).isGreaterThan(ror[atDrop] - 1.0);
    }

    @Test
    void jitterProducesDifferentButStillRealisticBatches() {
        Random random = new Random(11);
        for (int i = 0; i < 20; i++) {
            Spec spec = Spec.medium(100 + i).jitter(random, 1.0);
            RoastMetrics m = analyze(SyntheticRoastGenerator.generate(spec));

            assertThat(m.developmentTimeRatioPercent()).isBetween(10.0, 24.0);
            assertThat(m.totalTimeSeconds()).isBetween(480.0, 780.0);
        }
    }

    @Test
    void coolingSamplesFollowTheDrop() {
        GeneratedRoast roast = SyntheticRoastGenerator.generate(Spec.light(9).withCoolingSeconds(120));
        double drop = roast.events().get(EventType.DROP);

        assertThat(roast.curve().lastTime()).isGreaterThan(drop + 100);
        assertThat(roast.events().get(EventType.COOL)).isEqualTo(roast.curve().lastTime());
        double[] bt = roast.curve().beanTemp();
        assertThat(bt[bt.length - 1]).isLessThan(bt[indexAt(roast.curve().timeSeconds(), drop)] - 40);
    }

    @Test
    void csvRoundTripKeepsEventsWeightsAndSamples() {
        GeneratedRoast roast = SyntheticRoastGenerator.generate(Spec.medium(21).withGreenWeightG(500));

        ParsedRoast parsed = new CsvRoastParser().parse(SampleFormats.toCsv(roast, META));

        assertThat(parsed.events()).hasSize(4);
        roast.events().forEach((type, time) -> assertThat(parsed.events().get(type)).isCloseTo(time, within(0.01)));
        assertThat(parsed.greenWeightG()).isEqualTo(500.0);
        assertThat(parsed.roastedWeightG()).isEqualTo(roast.roastedWeightG());
        assertThat(parsed.title()).isEqualTo("Test roast");
        assertThat(parsed.roastedAt()).isEqualTo(META.roastedAt());
        assertThat(parsed.curve().lastTime()).isEqualTo(roast.events().get(EventType.DROP));
        assertThat(parsed.curve().beanTemp()[100]).isCloseTo(roast.curve().beanTemp()[100], within(0.05));
    }

    @Test
    void alogRoundTripThroughTheImporterTrimsCoolingAndKeepsEvents() {
        GeneratedRoast roast = SyntheticRoastGenerator.generate(Spec.dark(31).withCoolingSeconds(180));

        ParsedRoast parsed = new RoastLogImporter().importFile("dark.alog",
                SampleFormats.toAlog(roast, META).getBytes(StandardCharsets.UTF_8));

        assertThat(parsed.events()).doesNotContainKey(EventType.COOL);
        for (EventType type : Arrays.asList(EventType.DRY_END, EventType.FIRST_CRACK_START,
                EventType.FIRST_CRACK_END, EventType.DROP)) {
            assertThat(parsed.events().get(type)).as(type.name()).isCloseTo(roast.events().get(type), within(0.01));
        }
        assertThat(parsed.curve().lastTime()).isCloseTo(roast.events().get(EventType.DROP), within(0.01));
        assertThat(parsed.curve().firstTime()).isEqualTo(0.0);
        assertThat(parsed.greenWeightG()).isEqualTo(roast.greenWeightG());
        assertThat(parsed.roastedAt()).isEqualTo(META.roastedAt());
        assertThat(parsed.warnings()).anyMatch(w -> w.contains("after drop"));

        RoastMetrics fromFile = CurveAnalyzer.analyze(new AnalysisInput(parsed.curve(), parsed.events(),
                parsed.greenWeightG(), parsed.roastedWeightG()));
        RoastMetrics direct = analyze(roast);
        assertThat(fromFile.developmentTimeRatioPercent()).isCloseTo(direct.developmentTimeRatioPercent(), within(0.01));
        assertThat(fromFile.roastLossPercent()).isCloseTo(direct.roastLossPercent(), within(0.01));
    }

    private static int indexAt(double[] t, double seconds) {
        int i = 0;
        while (i < t.length - 1 && t[i] < seconds) {
            i++;
        }
        return i;
    }
}
