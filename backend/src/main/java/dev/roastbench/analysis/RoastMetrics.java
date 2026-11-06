package dev.roastbench.analysis;

import java.util.List;
import java.util.Map;

/**
 * Metrics derived from one roast.
 *
 * @param totalTimeSeconds            charge to drop (or to the last sample when drop was not marked)
 * @param dropEstimated               {@code true} when drop was not marked and the last sample was used
 * @param chargeBeanTemp              bean temperature at charge
 * @param dropBeanTemp                bean temperature at drop
 * @param turningPointSeconds         time of the turning point (minimum bean temperature after charge)
 * @param turningPointBeanTemp        bean temperature at the turning point
 * @param averageRorAfterTurningPoint mean RoR from turning point to drop in degrees Celsius per minute
 * @param phases                      drying / Maillard / development, only those that can be determined
 * @param developmentTimeRatioPercent development time divided by total time, in percent; {@code null} without first crack
 * @param roastLossPercent            weight loss in percent of green weight; {@code null} without both weights
 * @param events                      effective event times used (marked plus detected), seconds since charge
 */
public record RoastMetrics(double totalTimeSeconds, boolean dropEstimated, double chargeBeanTemp,
                           double dropBeanTemp, double turningPointSeconds, double turningPointBeanTemp,
                           double averageRorAfterTurningPoint, List<PhaseMetrics> phases,
                           Double developmentTimeRatioPercent, Double roastLossPercent,
                           Map<EventType, Double> events) {

    public RoastMetrics {
        phases = List.copyOf(phases);
        events = Map.copyOf(events);
    }

    public PhaseMetrics phase(PhaseMetrics.Phase which) {
        return phases.stream().filter(p -> p.phase() == which).findFirst().orElse(null);
    }

    public Double eventTime(EventType type) {
        return events.get(type);
    }
}
