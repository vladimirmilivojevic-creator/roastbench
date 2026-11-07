package dev.roastbench.analysis;

import dev.roastbench.analysis.ComparisonResult.PhaseDelta;
import java.util.ArrayList;
import java.util.List;

/** Compares one roast with a reference roast. Both roasts start at charge. */
public final class ReferenceComparator {

    private ReferenceComparator() {
    }

    public static ComparisonResult compare(RoastCurve roast, RoastMetrics roastMetrics, RoastCurve reference,
                                           RoastMetrics referenceMetrics) {
        double overlap = Math.floor(Math.min(roastMetrics.totalTimeSeconds(), referenceMetrics.totalTimeSeconds()));
        if (overlap < 1.0) {
            throw new AnalysisException("Roasts do not overlap in time");
        }
        int points = (int) overlap + 1;
        double[] rt = roast.timeSeconds();
        double[] rb = roast.beanTemp();
        double[] ft = reference.timeSeconds();
        double[] fb = reference.beanTemp();

        double[] grid = new double[points];
        double[] delta = new double[points];
        double sumAbs = 0;
        double sumSq = 0;
        double sum = 0;
        double maxAbs = -1;
        double maxAt = 0;
        for (int i = 0; i < points; i++) {
            double x = i;
            double d = CurveMath.valueAt(rt, rb, x) - CurveMath.valueAt(ft, fb, x);
            grid[i] = x;
            delta[i] = d;
            sum += d;
            sumAbs += Math.abs(d);
            sumSq += d * d;
            if (Math.abs(d) > maxAbs) {
                maxAbs = Math.abs(d);
                maxAt = x;
            }
        }

        List<PhaseDelta> phaseDeltas = new ArrayList<>();
        for (PhaseMetrics.Phase which : PhaseMetrics.Phase.values()) {
            PhaseMetrics a = roastMetrics.phase(which);
            PhaseMetrics b = referenceMetrics.phase(which);
            if (a != null && b != null) {
                phaseDeltas.add(new PhaseDelta(which, a.durationSeconds() - b.durationSeconds(),
                        a.averageRor() - b.averageRor()));
            }
        }

        return new ComparisonResult(overlap, grid, delta, sumAbs / points, maxAbs, maxAt, Math.sqrt(sumSq / points),
                sum / points,
                eventDelta(roastMetrics, referenceMetrics, EventType.DRY_END),
                eventDelta(roastMetrics, referenceMetrics, EventType.FIRST_CRACK_START),
                roastMetrics.totalTimeSeconds() - referenceMetrics.totalTimeSeconds(),
                roastMetrics.dropBeanTemp() - referenceMetrics.dropBeanTemp(),
                diff(roastMetrics.developmentTimeRatioPercent(), referenceMetrics.developmentTimeRatioPercent()),
                diff(roastMetrics.roastLossPercent(), referenceMetrics.roastLossPercent()), phaseDeltas);
    }

    private static Double eventDelta(RoastMetrics a, RoastMetrics b, EventType type) {
        return diff(a.eventTime(type), b.eventTime(type));
    }

    private static Double diff(Double a, Double b) {
        return a == null || b == null ? null : a - b;
    }
}
