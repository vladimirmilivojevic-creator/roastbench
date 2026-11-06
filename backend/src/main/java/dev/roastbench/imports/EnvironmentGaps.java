package dev.roastbench.imports;

/** Fills gaps (NaN) in the environment temperature channel so the curve can be validated and charted. */
final class EnvironmentGaps {

    private EnvironmentGaps() {
    }

    /**
     * Linearly interpolates NaN runs between valid readings and extends the first/last valid reading outwards.
     *
     * @return the filled array, or {@code null} when there is no valid reading at all (no such channel)
     */
    static double[] fill(double[] values) {
        int first = -1;
        for (int i = 0; i < values.length; i++) {
            if (!Double.isNaN(values[i])) {
                first = i;
                break;
            }
        }
        if (first < 0) {
            return null;
        }
        double[] out = values.clone();
        for (int i = 0; i < first; i++) {
            out[i] = out[first];
        }
        int previous = first;
        for (int i = first + 1; i < out.length; i++) {
            if (Double.isNaN(out[i])) {
                continue;
            }
            for (int j = previous + 1; j < i; j++) {
                double f = (double) (j - previous) / (i - previous);
                out[j] = out[previous] + f * (out[i] - out[previous]);
            }
            previous = i;
        }
        for (int i = previous + 1; i < out.length; i++) {
            out[i] = out[previous];
        }
        return out;
    }
}
