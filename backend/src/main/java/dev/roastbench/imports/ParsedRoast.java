package dev.roastbench.imports;

import dev.roastbench.analysis.EventType;
import dev.roastbench.analysis.RoastCurve;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** A roast file after reading. Time and events are from charge. Temperatures are in Celsius. */
public record ParsedRoast(String title, String beans, String machine, Instant roastedAt, RoastCurve curve,
                          Map<EventType, Double> events, Double greenWeightG, Double roastedWeightG,
                          SourceFormat format, List<String> warnings) {

    public ParsedRoast {
        events = Map.copyOf(events);
        warnings = List.copyOf(warnings);
    }
}
