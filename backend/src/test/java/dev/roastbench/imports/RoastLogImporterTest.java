package dev.roastbench.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.roastbench.analysis.EventType;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RoastLogImporterTest {

    private final RoastLogImporter importer = new RoastLogImporter();

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void choosesTheArtisanParserForAlogFiles() {
        ParsedRoast roast = importer.importFile("morning.alog", bytes(Fixtures.standardAlog("")));

        assertThat(roast.format()).isEqualTo(SourceFormat.ARTISAN_ALOG);
    }

    @Test
    void choosesTheCsvParserForCsvFiles() {
        ParsedRoast roast = importer.importFile("morning.csv", bytes(Fixtures.simpleCsv(",", 100, false)));

        assertThat(roast.format()).isEqualTo(SourceFormat.CSV);
    }

    @Test
    void sniffsContentWhenTheExtensionIsUnknown() {
        assertThat(importer.importFile("export", bytes(Fixtures.standardAlog(""))).format())
                .isEqualTo(SourceFormat.ARTISAN_ALOG);
        assertThat(importer.importFile("export", bytes(Fixtures.simpleCsv(";", 100, false))).format())
                .isEqualTo(SourceFormat.CSV);
    }

    @Test
    void trimsTheCoolingPhaseAfterDrop() {
        ParsedRoast roast = importer.importFile("a.alog", bytes(Fixtures.standardAlog("")));

        // Drop is sample 480 (952 s after charge); cooling samples up to 1192 s are cut.
        assertThat(roast.events()).containsEntry(EventType.DROP, 952.0).doesNotContainKey(EventType.COOL);
        assertThat(roast.curve().lastTime()).isEqualTo(952.0);
        assertThat(roast.warnings()).anyMatch(w -> w.contains("after drop"));
    }

    @Test
    void keepsTheCurveWhenThereIsNothingAfterDrop() {
        StringBuilder csv = new StringBuilder("Time,BT,Event\n");
        for (int t = 0; t <= 60; t += 2) {
            csv.append(t).append(',').append(100 + t).append(',').append(t == 60 ? "Drop" : "").append('\n');
        }

        ParsedRoast roast = importer.importFile("a.csv", bytes(csv.toString()));

        assertThat(roast.curve().lastTime()).isEqualTo(60.0);
        assertThat(roast.warnings()).noneMatch(w -> w.contains("after drop"));
    }

    @Test
    void cutsTheCurveAtTheDropSampleWhenSamplesFollow() {
        StringBuilder csv = new StringBuilder("Time,BT,Event\n");
        for (int t = 0; t <= 100; t += 2) {
            csv.append(t).append(',').append(100 + t).append(',').append(t == 50 ? "Drop" : "").append('\n');
        }

        ParsedRoast roast = importer.importFile("a.csv", bytes(csv.toString()));

        assertThat(roast.events()).containsEntry(EventType.DROP, 50.0);
        assertThat(roast.curve().lastTime()).isEqualTo(50.0);
        assertThat(roast.warnings()).anyMatch(w -> w.contains("25 samples after drop"));
    }

    @Test
    void fallsBackToLatinOneForNonUtf8Files() {
        String csv = "# title: Café ° test\n" + Fixtures.simpleCsv(",", 100, false);

        ParsedRoast roast = importer.importFile("a.csv", csv.getBytes(StandardCharsets.ISO_8859_1));

        assertThat(roast.title()).isEqualTo("Café ° test");
    }

    @Test
    void rejectsEmptyAndOversizedFiles() {
        assertThatThrownBy(() -> importer.importFile("a.csv", new byte[0]))
                .isInstanceOf(ImportException.class)
                .hasMessageContaining("empty");
        assertThatThrownBy(() -> importer.importFile("a.csv", new byte[RoastLogImporter.MAX_BYTES + 1]))
                .isInstanceOf(ImportException.class)
                .hasMessageContaining("larger than");
    }

    @Test
    void rejectsUnreadableContent() {
        assertThatThrownBy(() -> importer.importFile("a.alog", bytes("this is not a profile")))
                .isInstanceOf(ImportException.class);
        assertThatThrownBy(() -> importer.importFile("a.csv", bytes("hello\nworld\n")))
                .isInstanceOf(ImportException.class);
    }
}
