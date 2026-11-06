package dev.roastbench.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.roastbench.analysis.PhaseMetrics.Phase;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CurveAnalyzerTest {

    private static AnalysisInput input(Map<EventType, Double> events, Double green, Double roasted) {
        return new AnalysisInput(TestCurves.classicRoast(), events, green, roasted);
    }

    @Test
    void computesDevelopmentTimeRatioFromFirstCrackAndDrop() {
        RoastMetrics m = CurveAnalyzer.analyze(input(
                Map.of(EventType.DRY_END, 310.0, EventType.FIRST_CRACK_START, 500.0, EventType.DROP, 600.0),
                null, null));

        assertThat(m.totalTimeSeconds()).isEqualTo(600.0);
        assertThat(m.dropEstimated()).isFalse();
        assertThat(m.developmentTimeRatioPercent()).isCloseTo(100.0 / 600.0 * 100.0, within(1e-9));

        PhaseMetrics development = m.phase(Phase.DEVELOPMENT);
        assertThat(development.durationSeconds()).isCloseTo(100.0, within(1e-9));
        assertThat(development.startBeanTemp()).isCloseTo(188.0, within(1e-9));
        assertThat(development.endBeanTemp()).isCloseTo(208.0, within(1e-9));
        assertThat(development.averageRor()).isCloseTo(12.0, within(1e-9));
    }

    @Test
    void splitsRoastIntoThreePhasesThatAddUpToTheWholeRoast() {
        RoastMetrics m = CurveAnalyzer.analyze(input(
                Map.of(EventType.DRY_END, 310.0, EventType.FIRST_CRACK_START, 500.0, EventType.DROP, 600.0),
                null, null));

        assertThat(m.phases()).extracting(PhaseMetrics::phase)
                .containsExactly(Phase.DRYING, Phase.MAILLARD, Phase.DEVELOPMENT);
        assertThat(m.phases().stream().mapToDouble(PhaseMetrics::durationSeconds).sum()).isCloseTo(600.0, within(1e-9));
        assertThat(m.phases().stream().mapToDouble(PhaseMetrics::percentOfTotal).sum()).isCloseTo(100.0, within(1e-9));
        assertThat(m.phase(Phase.MAILLARD).durationSeconds()).isCloseTo(190.0, within(1e-9));
        assertThat(m.phase(Phase.DRYING).estimated()).isFalse();
    }

    @Test
    void dryingRorIsMeasuredFromTheTurningPoint() {
        RoastMetrics m = CurveAnalyzer.analyze(input(
                Map.of(EventType.DRY_END, 310.0, EventType.DROP, 600.0), null, null));

        // After the turning point the curve rises a constant 0.2 C/s = 12 C/min.
        assertThat(m.phase(Phase.DRYING).averageRor()).isCloseTo(12.0, within(0.05));
    }

    @Test
    void detectsTurningPointAsTheMinimumBeanTemperature() {
        RoastMetrics m = CurveAnalyzer.analyze(input(Map.of(EventType.DROP, 600.0), null, null));

        assertThat(m.turningPointSeconds()).isCloseTo(60.0, within(3.0));
        assertThat(m.turningPointBeanTemp()).isCloseTo(100.0, within(1.0));
        assertThat(m.eventTime(EventType.TURNING_POINT)).isNotNull();
    }

    @Test
    void usesMarkedTurningPointWhenProvided() {
        RoastMetrics m = CurveAnalyzer.analyze(input(
                Map.of(EventType.TURNING_POINT, 60.0, EventType.DROP, 600.0), null, null));

        assertThat(m.turningPointSeconds()).isEqualTo(60.0);
        assertThat(m.turningPointBeanTemp()).isCloseTo(100.0, within(1e-9));
    }

    @Test
    void estimatesDryEndAtHundredFiftyDegreesWhenNotMarked() {
        RoastMetrics m = CurveAnalyzer.analyze(input(
                Map.of(EventType.FIRST_CRACK_START, 500.0, EventType.DROP, 600.0), null, null));

        PhaseMetrics drying = m.phase(Phase.DRYING);
        assertThat(drying.estimated()).isTrue();
        assertThat(drying.endSeconds()).isCloseTo(310.0, within(1e-6));
        assertThat(m.phase(Phase.MAILLARD).estimated()).isTrue();
        assertThat(m.phase(Phase.DEVELOPMENT).estimated()).isFalse();
        assertThat(m.eventTime(EventType.DRY_END)).isCloseTo(310.0, within(1e-6));
    }

    @Test
    void markedDryEndOverridesEstimate() {
        RoastMetrics m = CurveAnalyzer.analyze(input(
                Map.of(EventType.DRY_END, 280.0, EventType.DROP, 600.0), null, null));

        assertThat(m.phase(Phase.DRYING).endSeconds()).isEqualTo(280.0);
        assertThat(m.phase(Phase.DRYING).estimated()).isFalse();
    }

    @Test
    void withoutFirstCrackThereIsNoDevelopmentPhaseAndNoDtr() {
        RoastMetrics m = CurveAnalyzer.analyze(input(Map.of(EventType.DROP, 600.0), null, null));

        assertThat(m.developmentTimeRatioPercent()).isNull();
        assertThat(m.phase(Phase.DEVELOPMENT)).isNull();
        assertThat(m.phase(Phase.MAILLARD)).isNull();
    }

    @Test
    void usesLastSampleAsDropWhenDropIsNotMarked() {
        RoastMetrics m = CurveAnalyzer.analyze(input(Map.of(EventType.FIRST_CRACK_START, 500.0), null, null));

        assertThat(m.dropEstimated()).isTrue();
        assertThat(m.totalTimeSeconds()).isEqualTo(600.0);
        assertThat(m.dropBeanTemp()).isCloseTo(208.0, within(1e-9));
    }

    @Test
    void computesRoastLossFromGreenAndRoastedWeight() {
        RoastMetrics m = CurveAnalyzer.analyze(input(Map.of(EventType.DROP, 600.0), 1000.0, 850.0));

        assertThat(m.roastLossPercent()).isCloseTo(15.0, within(1e-9));
    }

    @Test
    void roastLossIsNullWhenAWeightIsMissing() {
        assertThat(CurveAnalyzer.roastLoss(null, 850.0)).isNull();
        assertThat(CurveAnalyzer.roastLoss(1000.0, null)).isNull();
    }

    @Test
    void rejectsRoastedWeightAboveGreenWeight() {
        assertThatThrownBy(() -> CurveAnalyzer.roastLoss(1000.0, 1100.0))
                .isInstanceOf(AnalysisException.class)
                .hasMessageContaining("must not exceed");
    }

    @Test
    void rejectsNonPositiveWeights() {
        assertThatThrownBy(() -> CurveAnalyzer.roastLoss(0.0, 0.0)).isInstanceOf(AnalysisException.class);
        assertThatThrownBy(() -> CurveAnalyzer.roastLoss(500.0, -1.0)).isInstanceOf(AnalysisException.class);
    }

    @Test
    void rejectsEventsOutOfOrder() {
        assertThatThrownBy(() -> CurveAnalyzer.analyze(input(
                Map.of(EventType.DRY_END, 400.0, EventType.FIRST_CRACK_START, 300.0, EventType.DROP, 600.0), null,
                null)))
                .isInstanceOf(AnalysisException.class)
                .hasMessageContaining("First crack start")
                .hasMessageContaining("before Dry end");
    }

    @Test
    void rejectsDropBeyondTheLastSample() {
        assertThatThrownBy(() -> CurveAnalyzer.analyze(input(Map.of(EventType.DROP, 700.0), null, null)))
                .isInstanceOf(AnalysisException.class)
                .hasMessageContaining("after the last");
    }

    @Test
    void rejectsEventsBeforeCharge() {
        assertThatThrownBy(() -> CurveAnalyzer.analyze(input(
                Map.of(EventType.DRY_END, -5.0, EventType.DROP, 600.0), null, null)))
                .isInstanceOf(AnalysisException.class);
    }

    @Test
    void averageRorAfterTurningPointMatchesSlope() {
        RoastMetrics m = CurveAnalyzer.analyze(input(
                Map.of(EventType.TURNING_POINT, 60.0, EventType.DROP, 600.0), null, null));

        assertThat(m.averageRorAfterTurningPoint()).isCloseTo(12.0, within(1e-9));
        assertThat(m.chargeBeanTemp()).isCloseTo(200.0, within(1e-9));
    }
}
