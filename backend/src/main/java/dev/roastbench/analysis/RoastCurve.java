package dev.roastbench.analysis;

import java.util.Arrays;

/**
 * Immutable temperature curve of one roast.
 *
 * <p>Time is in seconds relative to CHARGE (t = 0), strictly increasing. Temperatures are in degrees Celsius.
 * The environment (drum/exhaust) temperature is optional. Accessors return copies, so fetch an array once
 * into a local variable when looping.
 */
public final class RoastCurve {

    private final double[] timeSeconds;
    private final double[] beanTemp;
    private final double[] envTemp;

    public RoastCurve(double[] timeSeconds, double[] beanTemp, double[] envTemp) {
        if (timeSeconds == null || beanTemp == null) {
            throw new AnalysisException("Curve needs time and bean temperature samples");
        }
        if (timeSeconds.length != beanTemp.length) {
            throw new AnalysisException("Time and bean temperature must have the same number of samples");
        }
        if (envTemp != null && envTemp.length != timeSeconds.length) {
            throw new AnalysisException("Environment temperature must have the same number of samples as time");
        }
        if (timeSeconds.length < 2) {
            throw new AnalysisException("Curve needs at least two samples");
        }
        for (int i = 0; i < timeSeconds.length; i++) {
            if (!Double.isFinite(timeSeconds[i]) || !Double.isFinite(beanTemp[i])
                    || (envTemp != null && !Double.isFinite(envTemp[i]))) {
                throw new AnalysisException("Curve contains a non-finite value at sample " + i);
            }
            if (i > 0 && timeSeconds[i] <= timeSeconds[i - 1]) {
                throw new AnalysisException("Time must be strictly increasing (sample " + i + ")");
            }
        }
        this.timeSeconds = timeSeconds.clone();
        this.beanTemp = beanTemp.clone();
        this.envTemp = envTemp == null ? null : envTemp.clone();
    }

    public double[] timeSeconds() {
        return timeSeconds.clone();
    }

    public double[] beanTemp() {
        return beanTemp.clone();
    }

    /** Environment temperature samples, or {@code null} when the log has no such channel. */
    public double[] envTemp() {
        return envTemp == null ? null : envTemp.clone();
    }

    public boolean hasEnvTemp() {
        return envTemp != null;
    }

    public int size() {
        return timeSeconds.length;
    }

    public double firstTime() {
        return timeSeconds[0];
    }

    public double lastTime() {
        return timeSeconds[timeSeconds.length - 1];
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RoastCurve other
                && Arrays.equals(timeSeconds, other.timeSeconds)
                && Arrays.equals(beanTemp, other.beanTemp)
                && Arrays.equals(envTemp, other.envTemp);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Arrays.hashCode(timeSeconds) + Arrays.hashCode(beanTemp)) + Arrays.hashCode(envTemp);
    }
}
