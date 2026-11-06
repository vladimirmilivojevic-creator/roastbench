package dev.roastbench.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class CurveMathTest {

    private final double[] t = {0, 10, 20, 30};
    private final double[] y = {100, 120, 110, 150};

    @Test
    void interpolatesLinearlyBetweenSamples() {
        assertThat(CurveMath.valueAt(t, y, 5)).isCloseTo(110.0, within(1e-12));
        assertThat(CurveMath.valueAt(t, y, 25)).isCloseTo(130.0, within(1e-12));
        assertThat(CurveMath.valueAt(t, y, 10)).isCloseTo(120.0, within(1e-12));
    }

    @Test
    void clampsOutsideTheSampledRange() {
        assertThat(CurveMath.valueAt(t, y, -5)).isEqualTo(100.0);
        assertThat(CurveMath.valueAt(t, y, 99)).isEqualTo(150.0);
    }

    @Test
    void findsInterpolatedRisingCrossing() {
        OptionalDouble crossing = CurveMath.firstRisingCrossing(t, y, 115, 0);

        assertThat(crossing).isPresent();
        assertThat(crossing.getAsDouble()).isCloseTo(7.5, within(1e-12));
    }

    @Test
    void ignoresCrossingsBeforeStartTime() {
        OptionalDouble crossing = CurveMath.firstRisingCrossing(t, y, 115, 12);

        assertThat(crossing).isPresent();
        // 110 at 20 s -> 150 at 30 s: 115 is reached after (115 - 110) / (150 - 110) of the 10 s step
        assertThat(crossing.getAsDouble()).isCloseTo(21.25, within(1e-12));
    }

    @Test
    void reportsNoCrossingWhenThresholdNeverReached() {
        assertThat(CurveMath.firstRisingCrossing(t, y, 500, 0)).isEmpty();
    }

    @Test
    void movingAverageShrinksWindowAtEnds() {
        double[] smooth = CurveMath.movingAverage(new double[] {0, 3, 6, 9, 12}, 3);

        assertThat(smooth[0]).isCloseTo(1.5, within(1e-12));
        assertThat(smooth[2]).isCloseTo(6.0, within(1e-12));
        assertThat(smooth[4]).isCloseTo(10.5, within(1e-12));
    }

    @Test
    void upperIndexIsFirstSampleAtOrAfterValue() {
        assertThat(CurveMath.upperIndex(t, 10)).isEqualTo(1);
        assertThat(CurveMath.upperIndex(t, 11)).isEqualTo(2);
        assertThat(CurveMath.upperIndex(t, 31)).isEqualTo(4);
    }
}
