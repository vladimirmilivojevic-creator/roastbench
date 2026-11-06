package dev.roastbench.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RorCalculatorTest {

    @Test
    void linearRampGivesConstantRorEverywhereIncludingEdges() {
        // +10 C per minute = 1/6 C per second
        RoastCurve curve = TestCurves.sample(300, 1, t -> 100 + t / 6.0);

        double[] ror = RorCalculator.compute(curve.timeSeconds(), curve.beanTemp(), 30);

        assertThat(ror).hasSize(301);
        assertThat(Arrays.stream(ror).boxed()).allSatisfy(v -> assertThat(v).isCloseTo(10.0, within(1e-9)));
    }

    @Test
    void constantTemperatureGivesZeroRor() {
        RoastCurve curve = TestCurves.sample(120, 1, t -> 180);

        double[] ror = RorCalculator.compute(curve.timeSeconds(), curve.beanTemp(), 30);

        assertThat(Arrays.stream(ror).boxed()).allSatisfy(v -> assertThat(v).isCloseTo(0.0, within(1e-12)));
    }

    @Test
    void symmetricWindowRecoversDerivativeOfQuadratic() {
        // T = 100 + 0.01 t^2  ->  dT/dt = 0.02 t  ->  at t = 100 s: 2 C/s = 120 C/min
        RoastCurve curve = TestCurves.sample(200, 1, t -> 100 + 0.01 * t * t);

        double[] ror = RorCalculator.compute(curve.timeSeconds(), curve.beanTemp(), 30);

        assertThat(ror[100]).isCloseTo(120.0, within(1e-6));
    }

    @Test
    void handlesIrregularSampling() {
        double[] t = {0, 1, 3, 4, 8, 9, 15, 16, 20, 25, 30};
        double[] y = new double[t.length];
        for (int i = 0; i < t.length; i++) {
            y[i] = 50 + 0.5 * t[i]; // 30 C/min
        }

        double[] ror = RorCalculator.compute(t, y, 20);

        assertThat(Arrays.stream(ror).boxed()).allSatisfy(v -> assertThat(v).isCloseTo(30.0, within(1e-9)));
    }

    @Test
    void smoothingReducesNoiseComparedWithRawDifferences() {
        double[] t = new double[121];
        double[] y = new double[121];
        for (int i = 0; i <= 120; i++) {
            t[i] = i;
            y[i] = 100 + i / 6.0 + ((i % 2 == 0) ? 0.2 : -0.2); // alternating +-0.2 C probe noise
        }

        double[] ror = RorCalculator.compute(t, y, 30);

        // A raw 1-second difference would swing by +-24 C/min; the regression stays within 1 C/min in the middle.
        assertThat(ror[60]).isCloseTo(10.0, within(1.0));
    }

    @Test
    void windowWiderThanDataStillWorks() {
        double[] t = {0, 1, 2};
        double[] y = {10, 10.5, 11};

        double[] ror = RorCalculator.compute(t, y, 120);

        assertThat(Arrays.stream(ror).boxed()).allSatisfy(v -> assertThat(v).isCloseTo(30.0, within(1e-9)));
    }

    @Test
    void rejectsWindowOutsideAllowedRange() {
        double[] t = {0, 1, 2};
        double[] y = {1, 2, 3};

        assertThatThrownBy(() -> RorCalculator.compute(t, y, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RorCalculator.compute(t, y, 500)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMismatchedArrays() {
        assertThatThrownBy(() -> RorCalculator.compute(new double[] {0, 1}, new double[] {1}, 30))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
