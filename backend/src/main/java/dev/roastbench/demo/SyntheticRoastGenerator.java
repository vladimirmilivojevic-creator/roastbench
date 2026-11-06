package dev.roastbench.demo;

import dev.roastbench.analysis.EventType;
import dev.roastbench.analysis.RoastCurve;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * Generates synthetic but physically plausible drum-roast curves for demo data and sample files.
 *
 * <p>These are <b>not</b> real roasts. Bean temperature follows
 * {@code BT(t) = floor + (charge - floor) * exp(-t / dipTau) + K * (1 - exp(-t / riseTau))}: the cold beans pull the
 * probe down right after charge, then the bean mass heats up along a decaying rate of rise. Environment
 * temperature is a smooth curve above bean temperature. Probe noise is small autocorrelated Gaussian noise with a
 * 0.1 degree resolution. The generator is deterministic for a given {@link Spec#seed()}.
 */
public final class SyntheticRoastGenerator {

    private static final double DRY_END_TEMP = 150.0;
    private static final double FIRST_CRACK_END_AFTER_START = 60.0;

    private SyntheticRoastGenerator() {
    }

    /**
     * @param seed                 noise seed
     * @param chargeTemp           probe reading at charge, degrees Celsius
     * @param floorTemp            asymptote of the initial dip (controls the turning point temperature)
     * @param riseAmplitude        K in the model, total temperature gain scale of the heating term
     * @param riseTauSeconds       time constant of the heating term (larger: slower roast)
     * @param dipTauSeconds        time constant of the initial dip
     * @param firstCrackTemp       bean temperature at which first crack starts
     * @param developmentSeconds   first crack start to drop
     * @param greenWeightG         charge weight in grams
     * @param lossPercent          weight loss in percent
     * @param sampleIntervalSeconds logging interval
     * @param coolingSeconds       samples to append after drop (cooling tray); 0 for none
     */
    public record Spec(long seed, double chargeTemp, double floorTemp, double riseAmplitude, double riseTauSeconds,
                       double dipTauSeconds, double firstCrackTemp, double developmentSeconds, double greenWeightG,
                       double lossPercent, double sampleIntervalSeconds, double coolingSeconds) {

        /** Light roast for filter: drop around 205 C, development time ratio about 13 %. */
        public static Spec light(long seed) {
            return new Spec(seed, 200, 78, 175, 520, 18, 196, 85, 1000, 12.5, 2, 0);
        }

        /** Medium roast: drop around 209 C, development time ratio about 16 %. */
        public static Spec medium(long seed) {
            return new Spec(seed, 200, 80, 185, 520, 18, 197, 105, 1000, 14.5, 2, 0);
        }

        /** Dark roast for espresso: drop around 214 C, development time ratio about 20 %. */
        public static Spec dark(long seed) {
            return new Spec(seed, 202, 80, 190, 500, 18, 196, 125, 1000, 17.0, 2, 0);
        }

        public Spec withGreenWeightG(double grams) {
            return new Spec(seed, chargeTemp, floorTemp, riseAmplitude, riseTauSeconds, dipTauSeconds, firstCrackTemp,
                    developmentSeconds, grams, lossPercent, sampleIntervalSeconds, coolingSeconds);
        }

        public Spec withCoolingSeconds(double seconds) {
            return new Spec(seed, chargeTemp, floorTemp, riseAmplitude, riseTauSeconds, dipTauSeconds, firstCrackTemp,
                    developmentSeconds, greenWeightG, lossPercent, sampleIntervalSeconds, seconds);
        }

        public Spec withSeed(long newSeed) {
            return new Spec(newSeed, chargeTemp, floorTemp, riseAmplitude, riseTauSeconds, dipTauSeconds,
                    firstCrackTemp, developmentSeconds, greenWeightG, lossPercent, sampleIntervalSeconds,
                    coolingSeconds);
        }

        /** Small run-to-run variation, as between two real batches of the same recipe. */
        public Spec jitter(Random random, double strength) {
            double s = strength;
            return new Spec(seed, chargeTemp + random.nextGaussian() * 2 * s, floorTemp + random.nextGaussian() * 2 * s,
                    riseAmplitude * (1 + random.nextGaussian() * 0.02 * s),
                    riseTauSeconds * (1 + random.nextGaussian() * 0.04 * s), dipTauSeconds, firstCrackTemp,
                    Math.max(40, developmentSeconds + random.nextGaussian() * 8 * s), greenWeightG,
                    Math.max(8, lossPercent + random.nextGaussian() * 0.4 * s), sampleIntervalSeconds, coolingSeconds);
        }
    }

    /**
     * @param curve          samples from charge (t = 0) to drop (plus cooling samples when requested)
     * @param events         DRY_END, FIRST_CRACK_START, FIRST_CRACK_END, DROP (and COOL with cooling), seconds since charge
     * @param greenWeightG   charge weight
     * @param roastedWeightG weight after roasting
     */
    public record GeneratedRoast(RoastCurve curve, Map<EventType, Double> events, double greenWeightG,
                                 double roastedWeightG) {
    }

    public static GeneratedRoast generate(Spec spec) {
        double dt = spec.sampleIntervalSeconds();
        double firstCrack = timeAtBeanTemp(spec, spec.firstCrackTemp());
        double dryEnd = timeAtBeanTemp(spec, DRY_END_TEMP);
        double drop = firstCrack + spec.developmentSeconds();
        double firstCrackEnd = Math.min(firstCrack + FIRST_CRACK_END_AFTER_START, drop - 10);

        double dropBt = cleanBeanTemp(spec, drop);
        int roastSamples = (int) Math.round(drop / dt) + 1;
        int coolSamples = (int) Math.round(spec.coolingSeconds() / dt);
        int n = roastSamples + coolSamples;

        Random random = new Random(spec.seed());
        double[] time = new double[n];
        double[] bt = new double[n];
        double[] et = new double[n];
        double btNoise = 0;
        double etNoise = 0;
        for (int i = 0; i < n; i++) {
            double t = i * dt;
            time[i] = t;
            btNoise = 0.6 * btNoise + 0.4 * 0.18 * random.nextGaussian();
            etNoise = 0.8 * etNoise + 0.2 * 0.4 * random.nextGaussian();
            double cleanBt = t <= drop ? cleanBeanTemp(spec, t)
                    : 25 + (dropBt - 25) * Math.exp(-(t - drop) / 110.0);
            bt[i] = round1(cleanBt + btNoise);
            double cleanEt = t <= drop ? cleanEnvTemp(spec, t)
                    : 25 + (cleanEnvTemp(spec, drop) - 25) * Math.exp(-(t - drop) / 140.0);
            et[i] = round1(cleanEt + etNoise);
        }

        Map<EventType, Double> events = new EnumMap<>(EventType.class);
        events.put(EventType.DRY_END, snap(dryEnd, dt));
        events.put(EventType.FIRST_CRACK_START, snap(firstCrack, dt));
        events.put(EventType.FIRST_CRACK_END, snap(firstCrackEnd, dt));
        events.put(EventType.DROP, snap(drop, dt));
        if (coolSamples > 0) {
            events.put(EventType.COOL, time[n - 1]);
        }

        double roasted = Math.round(spec.greenWeightG() * (1 - spec.lossPercent() / 100.0) * 10.0) / 10.0;
        return new GeneratedRoast(new RoastCurve(time, bt, et), events, spec.greenWeightG(), roasted);
    }

    static double cleanBeanTemp(Spec s, double t) {
        return s.floorTemp() + (s.chargeTemp() - s.floorTemp()) * Math.exp(-t / s.dipTauSeconds())
                + s.riseAmplitude() * (1 - Math.exp(-t / s.riseTauSeconds()));
    }

    static double cleanEnvTemp(Spec s, double t) {
        return 215 + 28 * (1 - Math.exp(-t / 350.0)) + (250 - 215) * Math.exp(-t / 40.0);
    }

    /** First time (after the turning point) at which the noise-free bean temperature reaches {@code temp}. */
    private static double timeAtBeanTemp(Spec spec, double temp) {
        double t = 120; // safely after the turning point
        while (cleanBeanTemp(spec, t) < temp) {
            t += 0.25;
            if (t > 3600) {
                throw new IllegalArgumentException("Spec never reaches " + temp + " C");
            }
        }
        return t;
    }

    private static double snap(double seconds, double interval) {
        return Math.round(seconds / interval) * interval;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
