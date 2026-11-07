package dev.roastbench.imports;

import dev.roastbench.analysis.AnalysisException;
import dev.roastbench.analysis.EventType;
import dev.roastbench.analysis.RoastCurve;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The entry point for uploaded files. It chooses the right reader (CSV or .alog) and removes the cooling part that
 * comes after drop.
 */
@Component
public class RoastLogImporter {

    public static final int MAX_BYTES = 5 * 1024 * 1024;

    private final ArtisanAlogParser alogParser = new ArtisanAlogParser();
    private final CsvRoastParser csvParser = new CsvRoastParser();

    public ParsedRoast importFile(String filename, byte[] content) {
        if (content == null || content.length == 0) {
            throw new ImportException("The file is empty");
        }
        if (content.length > MAX_BYTES) {
            throw new ImportException("The file is larger than " + (MAX_BYTES / 1024 / 1024) + " MB");
        }
        String text = decode(content);
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        ParsedRoast parsed;
        if (name.endsWith(".alog") || (!name.endsWith(".csv") && !name.endsWith(".tsv") && !name.endsWith(".txt")
                && text.stripLeading().startsWith("{"))) {
            parsed = alogParser.parse(text);
        } else {
            parsed = csvParser.parse(text);
        }
        return trimAfterDrop(parsed);
    }

    private static String decode(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException ex) {
            return new String(content, StandardCharsets.ISO_8859_1);
        }
    }

    /** Keeps samples up to and including the first one at or after drop; removes the cool-end event. */
    static ParsedRoast trimAfterDrop(ParsedRoast parsed) {
        Double drop = parsed.events().get(EventType.DROP);
        if (drop == null) {
            return parsed;
        }
        double[] t = parsed.curve().timeSeconds();
        int keep = t.length;
        for (int i = 0; i < t.length; i++) {
            if (t[i] >= drop) {
                keep = i + 1;
                break;
            }
        }
        Map<EventType, Double> events = new EnumMap<>(EventType.class);
        events.putAll(parsed.events());
        events.remove(EventType.COOL);
        if (keep >= t.length && !parsed.events().containsKey(EventType.COOL)) {
            return parsed;
        }
        List<String> warnings = new ArrayList<>(parsed.warnings());
        RoastCurve curve = parsed.curve();
        if (keep < t.length) {
            double[] bt = curve.beanTemp();
            double[] et = curve.envTemp();
            try {
                curve = new RoastCurve(java.util.Arrays.copyOf(t, keep), java.util.Arrays.copyOf(bt, keep),
                        et == null ? null : java.util.Arrays.copyOf(et, keep));
            } catch (AnalysisException ex) {
                throw new ImportException("Invalid curve data: " + ex.getMessage());
            }
            warnings.add((t.length - keep) + " samples after drop (cooling) were not imported");
        }
        return new ParsedRoast(parsed.title(), parsed.beans(), parsed.machine(), parsed.roastedAt(), curve, events,
                parsed.greenWeightG(), parsed.roastedWeightG(), parsed.format(), warnings);
    }
}
