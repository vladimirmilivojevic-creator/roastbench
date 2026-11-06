package dev.roastbench.imports;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EnvironmentGapsTest {

    @Test
    void interpolatesInteriorGapsAndExtendsEnds() {
        double nan = Double.NaN;

        double[] filled = EnvironmentGaps.fill(new double[] {nan, nan, 10, nan, nan, 16, nan});

        assertThat(filled).containsExactly(10, 10, 10, 12, 14, 16, 16);
    }

    @Test
    void returnsNullWhenThereIsNoReadingAtAll() {
        assertThat(EnvironmentGaps.fill(new double[] {Double.NaN, Double.NaN})).isNull();
    }

    @Test
    void leavesCompleteSeriesUntouched() {
        assertThat(EnvironmentGaps.fill(new double[] {1, 2, 3})).containsExactly(1, 2, 3);
    }
}
