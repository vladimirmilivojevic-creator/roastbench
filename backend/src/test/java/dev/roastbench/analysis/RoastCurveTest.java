package dev.roastbench.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RoastCurveTest {

    @Test
    void rejectsMismatchedLengths() {
        assertThatThrownBy(() -> new RoastCurve(new double[] {0, 1, 2}, new double[] {1, 2}, null))
                .isInstanceOf(AnalysisException.class);
        assertThatThrownBy(() -> new RoastCurve(new double[] {0, 1}, new double[] {1, 2}, new double[] {1}))
                .isInstanceOf(AnalysisException.class);
    }

    @Test
    void rejectsTooFewSamples() {
        assertThatThrownBy(() -> new RoastCurve(new double[] {0}, new double[] {1}, null))
                .isInstanceOf(AnalysisException.class)
                .hasMessageContaining("two samples");
    }

    @Test
    void rejectsNonIncreasingTime() {
        assertThatThrownBy(() -> new RoastCurve(new double[] {0, 1, 1}, new double[] {1, 2, 3}, null))
                .isInstanceOf(AnalysisException.class)
                .hasMessageContaining("strictly increasing");
    }

    @Test
    void rejectsNonFiniteValues() {
        assertThatThrownBy(() -> new RoastCurve(new double[] {0, 1}, new double[] {1, Double.NaN}, null))
                .isInstanceOf(AnalysisException.class);
        assertThatThrownBy(() -> new RoastCurve(new double[] {0, Double.POSITIVE_INFINITY}, new double[] {1, 2}, null))
                .isInstanceOf(AnalysisException.class);
    }

    @Test
    void isImmutableThroughConstructorAndAccessors() {
        double[] t = {0, 1};
        double[] bt = {100, 101};
        RoastCurve curve = new RoastCurve(t, bt, null);

        t[0] = 99;
        curve.beanTemp()[0] = -1;

        assertThat(curve.timeSeconds()[0]).isEqualTo(0);
        assertThat(curve.beanTemp()[0]).isEqualTo(100);
        assertThat(curve.hasEnvTemp()).isFalse();
        assertThat(curve.envTemp()).isNull();
    }
}
