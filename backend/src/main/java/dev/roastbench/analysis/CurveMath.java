package dev.roastbench.analysis;

import java.util.OptionalDouble;

/** Small math helpers for curves, for example: find the value between two samples. */
final class CurveMath {

    private CurveMath() {
    }

    /** Linear interpolation of {@code y(t)} at {@code x}; values outside the sampled range are clamped to the end points. */
    static double valueAt(double[] t, double[] y, double x) {
        int n = t.length;
        if (x <= t[0]) {
            return y[0];
        }
        if (x >= t[n - 1]) {
            return y[n - 1];
        }
        int hi = upperIndex(t, x);
        int lo = hi - 1;
        double f = (x - t[lo]) / (t[hi] - t[lo]);
        return y[lo] + f * (y[hi] - y[lo]);
    }

    /** First index {@code i} with {@code t[i] >= x} (binary search); {@code t.length} when none. */
    static int upperIndex(double[] t, double x) {
        int lo = 0;
        int hi = t.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (t[mid] < x) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /** First time at or after {@code fromTime} where {@code y} reaches {@code threshold} while rising, interpolated. */
    static OptionalDouble firstRisingCrossing(double[] t, double[] y, double threshold, double fromTime) {
        int start = Math.max(1, upperIndex(t, fromTime));
        for (int i = start; i < t.length; i++) {
            if (y[i - 1] < threshold && y[i] >= threshold) {
                double f = (threshold - y[i - 1]) / (y[i] - y[i - 1]);
                return OptionalDouble.of(t[i - 1] + f * (t[i] - t[i - 1]));
            }
        }
        return OptionalDouble.empty();
    }

    /** Centered moving average with a shrinking window at the ends. */
    static double[] movingAverage(double[] y, int window) {
        int half = Math.max(0, window / 2);
        double[] out = new double[y.length];
        for (int i = 0; i < y.length; i++) {
            int lo = Math.max(0, i - half);
            int hi = Math.min(y.length - 1, i + half);
            double sum = 0;
            for (int j = lo; j <= hi; j++) {
                sum += y[j];
            }
            out[i] = sum / (hi - lo + 1);
        }
        return out;
    }
}
