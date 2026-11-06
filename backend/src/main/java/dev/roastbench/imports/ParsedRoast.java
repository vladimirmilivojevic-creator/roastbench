package dev.roastbench.imports;

import dev.roastbench.analysis.EventType;
import dev.roastbench.analysis.RoastCurve;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A roast log after parsing: curve and events are relative to CHARGE, temperatures are in degrees Celsius.
 *
 * @param title          roast title from the file, may be {@code null}
 * @param beans          coffee description from the file, may be {@code null}
 * @param machine        roaster description from the file, may be {@code null}
 * @param roastedAt      when the roast happened, may be {@code null}
 * @param curve          temperature samples
 * @param events         marked events, seconds since charge
 * @param greenWeightG   green weight in grams, may be {@code null}
 * @param roastedWeightG roasted weight in grams, may be {@code null}
 * @param format         which parser produced this
 * @param warnings       non-fatal findings (dropped samples, assumptions) to show to the user
 */
public record ParsedRoast(String title, String beans, String machine, Instant roastedAt, RoastCurve curve,
                          Map<EventType, Double> events, Double greenWeightG, Double roastedWeightG,
                          SourceFormat format, List<String> warnings) {

    public ParsedRoast {
        events = Map.copyOf(events);
        warnings = List.copyOf(warnings);
    }
}
