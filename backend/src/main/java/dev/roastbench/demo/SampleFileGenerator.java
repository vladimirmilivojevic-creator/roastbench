package dev.roastbench.demo;

import dev.roastbench.demo.SampleFormats.Meta;
import dev.roastbench.demo.SyntheticRoastGenerator.Spec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Regenerates the files in {@code samples/}. Run with the output directory as the only argument:
 * {@code java -cp backend/target/classes dev.roastbench.demo.SampleFileGenerator samples}.
 */
public final class SampleFileGenerator {

    private SampleFileGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "samples");
        Files.createDirectories(out);

        write(out.resolve("ethiopia-guji-light.csv"), SampleFormats.toCsv(
                SyntheticRoastGenerator.generate(Spec.light(101).withGreenWeightG(500)),
                new Meta("Ethiopia Guji light", "Ethiopia Guji Natural", "Sample 1 kg drum roaster",
                        Instant.parse("2026-10-12T08:30:00Z"))));
        write(out.resolve("colombia-huila-medium.csv"), SampleFormats.toCsv(
                SyntheticRoastGenerator.generate(Spec.medium(202).withGreenWeightG(1000)),
                new Meta("Colombia Huila medium", "Colombia Huila Washed", "Sample 1 kg drum roaster",
                        Instant.parse("2026-10-14T09:15:00Z"))));
        write(out.resolve("brazil-cerrado-dark.alog"), SampleFormats.toAlog(
                SyntheticRoastGenerator.generate(Spec.dark(303).withGreenWeightG(1000).withCoolingSeconds(180)),
                new Meta("Brazil Cerrado dark", "Brazil Cerrado Natural", "Sample 1 kg drum roaster",
                        Instant.parse("2026-10-16T07:45:00Z"))));
        write(out.resolve("kenya-aa-light.alog"), SampleFormats.toAlog(
                SyntheticRoastGenerator.generate(Spec.light(404).withGreenWeightG(500).withCoolingSeconds(180)),
                new Meta("Kenya AA light", "Kenya Nyeri AA Washed", "Sample 1 kg drum roaster",
                        Instant.parse("2026-10-19T10:00:00Z"))));
        System.out.println("Sample files written to " + out.toAbsolutePath());
    }

    private static void write(Path file, String content) throws IOException {
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
