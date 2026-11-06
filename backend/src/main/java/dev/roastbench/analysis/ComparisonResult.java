package dev.roastbench.analysis;

import java.util.List;

/**
 * Difference between a roast and a reference roast. All deltas are {@code roast - reference}.
 * Curves are aligned at charge and compared on a 1-second grid over the time both roasts cover.
 *
 * @param overlapSeconds         length of the compared interval
 * @param gridSeconds            1-second grid, 0 .. overlapSeconds
 * @param beanTempDelta          bean temperature difference on that grid, degrees Celsius
 * @param meanAbsDeviation       mean of |delta|
 * @param maxAbsDeviation        largest |delta|
 * @param maxDeviationAtSeconds  time of the largest |delta|
 * @param rmse                   root mean square deviation
 * @param meanSignedDeviation    mean of delta (positive: roast ran hotter than the reference)
 * @param dryEndDeltaSeconds     difference of dry end times, {@code null} when either is unknown
 * @param firstCrackDeltaSeconds difference of first crack start times, {@code null} when either is unknown
 * @param dropDeltaSeconds       difference of total time
 * @param dropBeanTempDelta      difference of the drop temperature
 * @param dtrDeltaPercentPoints  difference of the development time ratio in percentage points
 * @param roastLossDeltaPercentPoints difference of roast loss in percentage points
 * @param phases                 per-phase differences for phases both roasts have
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
