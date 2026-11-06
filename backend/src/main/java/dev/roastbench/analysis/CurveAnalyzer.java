package dev.roastbench.analysis;

import dev.roastbench.analysis.PhaseMetrics.Phase;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Computes roast metrics: phases, development time ratio (DTR), roast loss and turning point.
 *
 * <p>Definitions used (all times in seconds since charge):
 * <ul>
 *   <li>total time = drop time (or the last sample when drop is not marked)</li>
 *   <li>drying = charge to dry end; Maillard = dry end to first crack start; development = first crack start to drop</li>
 *   <li>DTR = development time / total time x 100</li>
 *   <li>roast loss = (green weight - roasted weight) / green weight x 100</li>
 * </ul>
 * When dry end is not marked it is estimated as the first time the bean temperature reaches
 * {@value #ESTIMATED_DRY_END_TEMP} C after the turning point, and the phase is flagged {@code estimated}.
 */
public final class CurveAnalyzer {

    static final double ESTIMATED_DRY_END_TEMP = 150.0;
    private static final double TURNING_POINT_SEARCH_SECONDS = 240.0;
    private static final int TURNING_POINT_SMOOTHING_SAMPLES = 5;

    private CurveAnalyzer() {
    }

    public static RoastMetrics analyze(AnalysisInput input) {
        RoastCurve curve = input.curve();
        double[] t = curve.timeSeconds();
        double[] bt = curve.beanTemp();
        Map<EventType, Double> marked = input.events();

        double dropTime = marked.getOrDefault(EventType.DROP, t[t.length - 1]);
        boolean dropEstimated = !marked.containsKey(EventType.DROP);
        if (dropTime <= 0) {
            throw new AnalysisException("Drop must be after charge");
        }
        if (dropTime > t[t.length - 1] + 1e-6) {
            throw new AnalysisException("Drop is after the last temperature sample");
        }
        validateEventOrder(marked, dropTime);

        double chargeTemp = CurveMath.valueAt(t, bt, 0.0);
        double dropTemp = CurveMath.valueAt(t, bt, dropTime);

        Map<EventType, Double> effective = new EnumMap<>(EventType.class);
        effective.putAll(marked);
        effective.put(EventType.DROP, dropTime);

        double tpTime;
        if (marked.containsKey(EventType.TURNING_POINT)) {
            tpTime = marked.get(EventType.TURNING_POINT);
        } else {
            tpTime = detectTurningPoint(t, bt, dropTime);
            effective.put(EventType.TURNING_POINT, tpTime);
        }
        double tpTemp = CurveMath.valueAt(t, bt, tpTime);

        Double dryEnd = marked.get(EventType.DRY_END);
        boolean dryEndEstimated = false;
        if (dryEnd == null) {
            OptionalDouble crossing = CurveMath.firstRisingCrossing(t, bt, ESTIMATED_DRY_END_TEMP, tpTime);
            if (crossing.isPresent() && crossing.getAsDouble() < dropTime) {
                dryEnd = crossing.getAsDouble();
                dryEndEstimated = true;
                effective.put(EventType.DRY_END, dryEnd);
            }
        }
        Double fcStart = marked.get(EventType.FIRST_CRACK_START);

        List<PhaseMetrics> phases = new ArrayList<>(3);
        if (dryEnd != null) {
            // The probe is still hot from the empty drum at charge, so the drying RoR is measured from the turning point.
            double dryingRorStart = tpTime < dryEnd ? tpTime : 0.0;
            phases.add(phase(Phase.DRYING, 0.0, dryEnd, dryingRorStart, dropTime, t, bt, dryEndEstimated));
        }
        if (dryEnd != null && fcStart != null) {
            phases.add(phase(Phase.MAILLARD, dryEnd, fcStart, dryEnd, dropTime, t, bt, dryEndEstimated));
        }
        Double dtr = null;
        if (fcStart != null) {
            PhaseMetrics development = phase(Phase.DEVELOPMENT, fcStart, dropTime, fcStart, dropTime, t, bt, false);
            phases.add(development);
            dtr = development.durationSeconds() / dropTime * 100.0;
        }

        double rorAfterTp = dropTime > tpTime ? (dropTemp - tpTemp) / (dropTime - tpTime) * 60.0 : 0.0;

        return new RoastMetrics(dropTime, dropEstimated, chargeTemp, dropTemp, tpTime, tpTemp, rorAfterTp, phases,
                dtr, roastLoss(input.greenWeightG(), input.roastedWeightG()), effective);
    }

    /** Weight loss in percent of the green weight, or {@code null} when a weight is missing. */
    public static Double roastLoss(Double greenWeightG, Double roastedWeightG) {
        if (greenWeightG == null || roastedWeightG == null) {
            return null;
        }
        if (greenWeightG <= 0 || roastedWeightG <= 0) {
            throw new AnalysisException("Weights must be positive");
        }
        if (roastedWeightG > greenWeightG) {
            throw new AnalysisException("Roasted weight must not exceed green weight");
        }
        return (greenWeightG - roastedWeightG) / greenWeightG * 100.0;
    }

    private static PhaseMetrics phase(Phase phase, double start, double end, double rorStart, double total,
                                      double[] t, double[] bt, boolean estimated) {
        double duration = end - start;
        double startTemp = CurveMath.valueAt(t, bt, start);
        double endTemp = CurveMath.valueAt(t, bt, end);
        double rorDuration = end - rorStart;
        double ror = rorDuration > 0 ? (endTemp - CurveMath.valueAt(t, bt, rorStart)) / rorDuration * 60.0 : 0.0;
        return new PhaseMetrics(phase, start, end, duration, duration / total * 100.0, startTemp, endTemp, ror,
                estimated);
    }

    /** Minimum of the lightly smoothed bean temperature within the first minutes after charge. */
    private static double detectTurningPoint(double[] t, double[] bt, double dropTime) {
        double limit = Math.min(TURNING_POINT_SEARCH_SECONDS, dropTime);
        int end = CurveMath.upperIndex(t, limit);
        end = Math.min(Math.max(end, 2), t.length);
        double[] window = new double[end];
        System.arraycopy(bt, 0, window, 0, end);
        double[] smooth = CurveMath.movingAverage(window, TURNING_POINT_SMOOTHING_SAMPLES);
        int best = 0;
        for (int i = 1; i < smooth.length; i++) {
            if (smooth[i] < smooth[best]) {
                best = i;
            }
        }
        return t[best];
    }

    private static void validateEventOrder(Map<EventType, Double> events, double dropTime) {
        double previous = 0.0;
        EventType previousType = EventType.CHARGE;
        for (EventType type : new EventType[] {EventType.TURNING_POINT, EventType.DRY_END,
                EventType.FIRST_CRACK_START, EventType.FIRST_CRACK_END, EventType.SECOND_CRACK_START,
                EventType.SECOND_CRACK_END, EventType.DROP}) {
            Double time = events.get(type);
            if (time == null) {
                continue;
            }
            if (time < 0) {
                throw new AnalysisException(type.label() + " is before charge");
            }
            if (time < previous) {
                throw new AnalysisException(type.label() + " (" + Math.round(time) + " s) is before "
                        + previousType.label() + " (" + Math.round(previous) + " s)");
            }
            previous = time;
            previousType = type;
        }
        Double cool = events.get(EventType.COOL);
        if (cool != null && cool < dropTime) {
            throw new AnalysisException("Cool end is before drop");
        }
    }
}
