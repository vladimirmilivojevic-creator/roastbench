package dev.roastbench.analysis;

/**
 * Rate of rise (RoR) in degrees Celsius per minute.
 *
 * <p>For every sample the RoR is the slope of an ordinary least-squares line fitted to all bean-temperature samples
 * inside a window centred on that sample. A regression slope is far less noisy than a plain
 * {@code (T2 - T1) / (t2 - t1)} difference, which matters because probes are read at 1-3 s intervals and have
 * 0.1-0.5 degree resolution. At the start and end of the curve the window is truncated to the available samples.
 */
public final class RorCalculator {

    public static final double DEFAULT_WINDOW_SECONDS = 30.0;
    public static final double MIN_WINDOW_SECONDS = 5.0;
    public static final double MAX_WINDOW_SECONDS = 120.0;

    private RorCalculator() {
    }

    public static double[] compute(double[] timeSeconds, double[] temp, double windowSeconds) {
        if (timeSeconds.length != temp.length) {
            throw new IllegalArgumentException("time and temperature length differ");
        }
        if (windowSeconds < MIN_WINDOW_SECONDS || windowSeconds > MAX_WINDOW_SECONDS) {
            throw new IllegalArgumentException("RoR window must be between " + MIN_WINDOW_SECONDS + " and "
                    + MAX_WINDOW_SECONDS + " seconds");
        }
        int n = timeSeconds.length;
        double[] ror = new double[n];
        double half = windowSeconds / 2.0;
        int lo = 0;
        int hi = 0;
        for (int i = 0; i < n; i++) {
            double t = timeSeconds[i];
            while (timeSeconds[lo] < t - half) {
                lo++;
            }
            while (hi + 1 < n && timeSeconds[hi + 1] <= t + half) {
                hi++;
            }
            ror[i] = slopePerMinute(timeSeconds, temp, lo, hi);
        }
        return ror;
    }

    private static double slopePerMinute(double[] t, double[] y, int lo, int hi) {
        int count = hi - lo + 1;
        if (count < 2) {
            return 0.0;
        }
        double meanT = 0;
        double meanY = 0;
        for (int j = lo; j <= hi; j++) {
            meanT += t[j];
            meanY += y[j];
        }
        meanT /= count;
        meanY /= count;
        double sxx = 0;
        double sxy = 0;
        for (int j = lo; j <= hi; j++) {
            double dt = t[j] - meanT;
            sxx += dt * dt;
            sxy += dt * (y[j] - meanY);
        }
        return sxx == 0 ? 0.0 : (sxy / sxx) * 60.0;
    }
}
