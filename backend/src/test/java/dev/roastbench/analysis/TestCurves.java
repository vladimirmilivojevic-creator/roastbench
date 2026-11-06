package dev.roastbench.analysis;

import java.util.function.DoubleUnaryOperator;

/** Hand-built curves with known, easy-to-verify properties. */
final class TestCurves {

    private TestCurves() {
    }

    static RoastCurve sample(double durationSeconds, double stepSeconds, DoubleUnaryOperator bean) {
        int n = (int) Math.round(durationSeconds / stepSeconds) + 1;
        double[] t = new double[n];
        double[] bt = new double[n];
        for (int i = 0; i < n; i++) {
            t[i] = i * stepSeconds;
            bt[i] = bean.applyAsDouble(t[i]);
        }
        return new RoastCurve(t, bt, null);
    }

    /**
     * 0-600 s at 1 s. Bean temperature falls from 200 C at charge to 100 C at 60 s (turning point), then rises
     * 0.2 C/s (12 C/min): 150 C at 310 s, 188 C at 500 s, 208 C at 600 s.
     */
    static RoastCurve classicRoast() {
        return sample(600, 1, t -> t < 60 ? 200 - 100 * (t / 60.0) : 100 + 0.2 * (t - 60));
    }

    /** Same shape as {@link #classicRoast()} but {@code offset} degrees hotter everywhere. */
    static RoastCurve classicRoastOffset(double offset) {
        return sample(600, 1, t -> (t < 60 ? 200 - 100 * (t / 60.0) : 100 + 0.2 * (t - 60)) + offset);
    }
}
