package dev.roastbench.analysis;

import java.util.List;

/**
 * The difference between a roast and the reference roast. A difference is always "roast minus reference", so a plus
 * means the roast is hotter, later or higher. The temperature difference is given for every second.
 */
public record ComparisonResult(double overlapSeconds, double[] gridSeconds, double[] beanTempDelta,
                               double meanAbsDeviation, double maxAbsDeviation, double maxDeviationAtSeconds,
                               double rmse, double meanSignedDeviation, Double dryEndDeltaSeconds,
                               Double firstCrackDeltaSeconds, double dropDeltaSeconds, double dropBeanTempDelta,
                               Double dtrDeltaPercentPoints, Double roastLossDeltaPercentPoints,
                               List<PhaseDelta> phases) {

    public ComparisonResult {
        phases = List.copyOf(phases);
    }

    public record PhaseDelta(PhaseMetrics.Phase phase, double durationDeltaSeconds, double averageRorDelta) {
    }
}
