package dev.roastbench.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.roastbench.analysis.PhaseMetrics.Phase;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceComparatorTest {

    private static RoastMetrics metrics(RoastCurve curve, Map<EventType, Double> events, Double green,
                                        Double roasted) {
        return CurveAnalyzer.analyze(new AnalysisInput(curve, events, green, roasted));
    }

    private static final Map<EventType, Double> EVENTS =
            Map.of(EventType.DRY_END, 310.0, EventType.FIRST_CRACK_START, 500.0, EventType.DROP, 600.0);

    @Test
    void identicalRoastsHaveNoDeviation() {
        RoastCurve curve = TestCurves.classicRoast();
        RoastMetrics m = metrics(curve, EVENTS, 1000.0, 850.0);

        ComparisonResult result = ReferenceComparator.compare(curve, m, curve, m);

        assertThat(result.overlapSeconds()).isEqualTo(600.0);
        assertThat(result.gridSeconds()).hasSize(601);
        assertThat(result.maxAbsDeviation()).isCloseTo(0.0, within(1e-12));
        assertThat(result.rmse()).isCloseTo(0.0, within(1e-12));
        assertThat(result.dropDeltaSeconds()).isEqualTo(0.0);
        assertThat(result.dtrDeltaPercentPoints()).isCloseTo(0.0, within(1e-12));
    }

    @Test
    void constantOffsetShowsUpInAllDeviationStatistics() {
        RoastCurve hot = TestCurves.classicRoastOffset(5);
        RoastCurve ref = TestCurves.classicRoast();

        ComparisonResult result = ReferenceComparator.compare(hot, metrics(hot, EVENTS, null, null), ref,
                metrics(ref, EVENTS, null, null));

        assertThat(result.meanSignedDeviation()).isCloseTo(5.0, within(1e-9));
        assertThat(result.meanAbsDeviation()).isCloseTo(5.0, within(1e-9));
        assertThat(result.rmse()).isCloseTo(5.0, within(1e-9));
        assertThat(result.maxAbsDeviation()).isCloseTo(5.0, within(1e-9));
        assertThat(Arrays.stream(result.beanTempDelta()).boxed())
                .allSatisfy(d -> assertThat(d).isCloseTo(5.0, within(1e-9)));
    }

    @Test
    void reportsWhereTheLargestDeviationHappens() {
        RoastCurve ref = TestCurves.classicRoast();
        // Differs from the reference only between 200 s and 300 s, peaking at 250 s.
        RoastCurve bump = TestCurves.sample(600, 1, t -> {
            double base = t < 60 ? 200 - 100 * (t / 60.0) : 100 + 0.2 * (t - 60);
            return t >= 200 && t <= 300 ? base + 10 - Math.abs(t - 250) / 5.0 : base;
        });

        ComparisonResult result = ReferenceComparator.compare(bump, metrics(bump, EVENTS, null, null), ref,
                metrics(ref, EVENTS, null, null));

        assertThat(result.maxDeviationAtSeconds()).isEqualTo(250.0);
        assertThat(result.maxAbsDeviation()).isCloseTo(10.0, within(1e-9));
    }

    @Test
    void comparesOnlyTheTimeBothRoastsCover() {
        RoastCurve ref = TestCurves.classicRoast();
        Map<EventType, Double> earlyDrop = Map.of(EventType.DROP, 540.0);
        RoastMetrics shortMetrics = metrics(ref, earlyDrop, null, null);

        ComparisonResult result = ReferenceComparator.compare(ref, shortMetrics, ref, metrics(ref, EVENTS, null, null));

        assertThat(result.overlapSeconds()).isEqualTo(540.0);
        assertThat(result.dropDeltaSeconds()).isEqualTo(-60.0);
        assertThat(result.dropBeanTempDelta()).isCloseTo(-12.0, within(1e-9));
    }

    @Test
    void reportsEventPhaseDtrAndLossDeltas() {
        RoastCurve curve = TestCurves.classicRoast();
        Map<EventType, Double> lateCrack =
                Map.of(EventType.DRY_END, 310.0, EventType.FIRST_CRACK_START, 520.0, EventType.DROP, 600.0);
        RoastMetrics roast = metrics(curve, lateCrack, 1000.0, 860.0);
        RoastMetrics reference = metrics(curve, EVENTS, 1000.0, 850.0);

        ComparisonResult result = ReferenceComparator.compare(curve, roast, curve, reference);

        assertThat(result.firstCrackDeltaSeconds()).isEqualTo(20.0);
        assertThat(result.dryEndDeltaSeconds()).isEqualTo(0.0);
        assertThat(result.dtrDeltaPercentPoints()).isCloseTo((80.0 - 100.0) / 600.0 * 100.0, within(1e-9));
        assertThat(result.roastLossDeltaPercentPoints()).isCloseTo(-1.0, within(1e-9));
        assertThat(result.phases()).extracting(ComparisonResult.PhaseDelta::phase)
                .containsExactly(Phase.DRYING, Phase.MAILLARD, Phase.DEVELOPMENT);
        ComparisonResult.PhaseDelta development = result.phases().get(2);
        assertThat(development.durationDeltaSeconds()).isCloseTo(-20.0, within(1e-9));
    }

    @Test
    void leavesDeltasNullWhenOneSideLacksTheData() {
        RoastCurve curve = TestCurves.classicRoast();
        RoastMetrics withCrack = metrics(curve, EVENTS, 1000.0, 850.0);
        RoastMetrics withoutCrack = metrics(curve, Map.of(EventType.DROP, 600.0), null, null);

        ComparisonResult result = ReferenceComparator.compare(curve, withoutCrack, curve, withCrack);

        assertThat(result.firstCrackDeltaSeconds()).isNull();
        assertThat(result.dtrDeltaPercentPoints()).isNull();
        assertThat(result.roastLossDeltaPercentPoints()).isNull();
    }

    @Test
    void rejectsRoastsWithoutOverlap() {
        RoastCurve curve = TestCurves.classicRoast();
        RoastCurve tiny = new RoastCurve(new double[] {0, 0.5}, new double[] {200, 190}, null);
        RoastMetrics tinyMetrics = CurveAnalyzer.analyze(new AnalysisInput(tiny, Map.of(), null, null));
        RoastMetrics normal = metrics(curve, EVENTS, null, null);

        assertThatThrownBy(() -> ReferenceComparator.compare(tiny, tinyMetrics, curve, normal))
                .isInstanceOf(AnalysisException.class);
    }
}
