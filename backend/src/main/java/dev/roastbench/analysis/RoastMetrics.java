package dev.roastbench.analysis;

import java.util.List;
import java.util.Map;

/** All numbers calculated for one roast: times, temperatures, phases, DTR and roast loss. */
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
