package dev.roastbench.demo;

import dev.roastbench.analysis.EventType;
import dev.roastbench.demo.SyntheticRoastGenerator.GeneratedRoast;
import dev.roastbench.demo.SyntheticRoastGenerator.Spec;
import dev.roastbench.imports.ParsedRoast;
import dev.roastbench.imports.SourceFormat;
import dev.roastbench.inventory.InventoryDtos.LocationDto;
import dev.roastbench.inventory.InventoryDtos.LocationRequest;
import dev.roastbench.inventory.InventoryDtos.LotDto;
import dev.roastbench.inventory.InventoryDtos.LotRequest;
import dev.roastbench.inventory.InventoryDtos.AdjustmentRequest;
import dev.roastbench.inventory.InventoryDtos.ReceiptRequest;
import dev.roastbench.inventory.InventoryDtos.TransferRequest;
import dev.roastbench.inventory.InventoryService;
import dev.roastbench.inventory.LotRepository;
import dev.roastbench.profile.ProfileService;
import dev.roastbench.profile.ProfileService.ProfileDto;
import dev.roastbench.profile.ProfileService.ProfileRequest;
import dev.roastbench.roast.RoastDtos.ImportRequest;
import dev.roastbench.roast.RoastDtos.LotWeight;
import dev.roastbench.roast.RoastDtos.RoastDetailDto;
import dev.roastbench.roast.RoastRepository;
import dev.roastbench.roast.RoastService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fills the database with a small synthetic roastery: lots, stock movements, profiles and about twenty roasts.
 * Everything goes through the same services the API uses, so the demo data obeys the same rules as real data.
 * All curves are generated; none of it comes from a real roaster.
 */
@Service
public class DemoDataService {

    private static final Logger log = LoggerFactory.getLogger(DemoDataService.class);
    private static final String MACHINE = "Demo 5 kg drum roaster";

    private record LotSeed(String code, String name, String country, String region, String producer, String variety,
                           String process, int altitude, int cropYear, String supplier, String price, String minKg,
                           String receivedKg) {
    }

    private record RoastSeed(String profile, String name, int daysAgo, int hour, Spec spec, double jitter,
                             Map<String, Integer> gramsByLot) {
    }

    private static final List<LotSeed> LOTS = List.of(
            new LotSeed("ETH-GUJI-25", "Guji Hambela Natural", "Ethiopia", "Guji", "Hambela smallholders",
                    "Heirloom", "Natural", 2050, 2025, "Demo Green Importer", "14.80", "20", "60"),
            new LotSeed("COL-HUILA-25", "Huila Pitalito Washed", "Colombia", "Huila", "Pitalito cooperative",
                    "Caturra, Castillo", "Washed", 1750, 2025, "Demo Green Importer", "9.60", "30", "120"),
            new LotSeed("BRA-CERR-25", "Cerrado Mineiro Natural", "Brazil", "Cerrado Mineiro", "Fazenda Demo",
                    "Yellow Bourbon", "Natural", 1100, 2025, "Demo Green Importer", "7.40", "40", "150"),
            new LotSeed("KEN-NYERI-25", "Nyeri AA Washed", "Kenya", "Nyeri", "Demo Farmers Society", "SL28, SL34",
                    "Washed", 1800, 2025, "Demo Green Importer", "17.90", "15", "12"),
            new LotSeed("GUA-ANTI-25", "Antigua Washed", "Guatemala", "Antigua", "Finca Demo", "Bourbon",
                    "Washed", 1600, 2025, "Demo Green Importer", "9.10", "20", "45"),
            new LotSeed("RWA-NYAM-25", "Nyamasheke Washed", "Rwanda", "Nyamasheke", "Demo washing station",
                    "Red Bourbon", "Washed", 1750, 2025, "Demo Green Importer", "11.30", "15", "22"),
            new LotSeed("SUM-MAND-25", "Mandheling Wet-hulled", "Indonesia", "Sumatra", "Demo collectors",
                    "Ateng, Typica", "Wet-hulled", 1300, 2025, "Demo Green Importer", "8.20", "10", "10"));

    private static final String FILTER = "Filter light";
    private static final String MEDIUM = "Medium all-rounder";
    private static final String ESPRESSO = "Espresso dark blend";

    private static List<RoastSeed> roastSeeds() {
        List<RoastSeed> r = new ArrayList<>();
        // Filter light: Ethiopia and Kenya, 3 kg and 2 kg batches. The second batch is the reference.
        r.add(new RoastSeed(FILTER, "Guji Natural filter 1", 40, 8, Spec.light(11).withGreenWeightG(3000), 1, lots("ETH-GUJI-25", 3000)));
        r.add(new RoastSeed(FILTER, "Guji Natural filter 2", 33, 9, Spec.light(12).withGreenWeightG(3000), 0.6, lots("ETH-GUJI-25", 3000)));
        r.add(new RoastSeed(FILTER, "Nyeri AA filter 1", 26, 8, Spec.light(13).withGreenWeightG(2000), 1, lots("KEN-NYERI-25", 2000)));
        r.add(new RoastSeed(FILTER, "Guji Natural filter 3", 19, 9, Spec.light(14).withGreenWeightG(3000), 1.2, lots("ETH-GUJI-25", 3000)));
        r.add(new RoastSeed(FILTER, "Nyeri AA filter 2", 12, 8, Spec.light(15).withGreenWeightG(2000), 1.4, lots("KEN-NYERI-25", 2000)));
        r.add(new RoastSeed(FILTER, "Guji Natural filter 4", 5, 10, Spec.light(16).withGreenWeightG(3000), 2.6, lots("ETH-GUJI-25", 3000)));
        // Medium all-rounder: single origins in 5 kg batches.
        r.add(new RoastSeed(MEDIUM, "Huila Washed medium 1", 38, 11, Spec.medium(21).withGreenWeightG(5000), 1, lots("COL-HUILA-25", 5000)));
        r.add(new RoastSeed(MEDIUM, "Antigua Washed medium 1", 31, 11, Spec.medium(22).withGreenWeightG(5000), 0.6, lots("GUA-ANTI-25", 5000)));
        r.add(new RoastSeed(MEDIUM, "Huila Washed medium 2", 24, 12, Spec.medium(23).withGreenWeightG(5000), 1, lots("COL-HUILA-25", 5000)));
        r.add(new RoastSeed(MEDIUM, "Nyamasheke medium 1", 17, 11, Spec.medium(24).withGreenWeightG(5000), 1.2, lots("RWA-NYAM-25", 5000)));
        r.add(new RoastSeed(MEDIUM, "Mandheling medium 1", 14, 12, Spec.medium(25).withGreenWeightG(5000), 1.5, lots("SUM-MAND-25", 5000)));
        r.add(new RoastSeed(MEDIUM, "Huila Washed medium 3", 10, 11, Spec.medium(26).withGreenWeightG(5000), 1, lots("COL-HUILA-25", 5000)));
        r.add(new RoastSeed(MEDIUM, "Nyamasheke medium 2", 9, 12, Spec.medium(27).withGreenWeightG(5000), 1.8, lots("RWA-NYAM-25", 5000)));
        r.add(new RoastSeed(MEDIUM, "Mandheling medium 2", 6, 11, Spec.medium(28).withGreenWeightG(5000), 2.2, lots("SUM-MAND-25", 5000)));
        r.add(new RoastSeed(MEDIUM, "Antigua Washed medium 2", 3, 11, Spec.medium(29).withGreenWeightG(5000), 1, lots("GUA-ANTI-25", 5000)));
        r.add(new RoastSeed(MEDIUM, "Nyamasheke medium 3", 2, 12, Spec.medium(30).withGreenWeightG(5000), 1, lots("RWA-NYAM-25", 5000)));
        // Espresso dark blend: 60 % Brazil, 40 % Colombia, 5 kg batches.
        int[] days = {36, 29, 22, 15, 8, 1};
        for (int i = 0; i < days.length; i++) {
            Map<String, Integer> blend = new HashMap<>();
            blend.put("BRA-CERR-25", 3000);
            blend.put("COL-HUILA-25", 2000);
            r.add(new RoastSeed(ESPRESSO, "House espresso " + (i + 1), days[i], 13, Spec.dark(40 + i).withGreenWeightG(5000),
                    i == 5 ? 2.4 : 1, blend));
        }
        return r;
    }

    private static Map<String, Integer> lots(String code, int grams) {
        return Map.of(code, grams);
    }

    private final InventoryService inventory;
    private final ProfileService profiles;
    private final RoastService roastService;
    private final RoastRepository roasts;
    private final LotRepository lots;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DemoDataService(InventoryService inventory, ProfileService profiles, RoastService roastService,
                           RoastRepository roasts, LotRepository lots, JdbcTemplate jdbc, Clock clock) {
        this.inventory = inventory;
        this.profiles = profiles;
        this.roastService = roastService;
        this.roasts = roasts;
        this.lots = lots;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Seeds an empty database; does nothing when there is already data. */
    @Transactional
    public boolean seedIfEmpty() {
        if (roasts.count() > 0 || lots.count() > 0) {
            return false;
        }
        seed();
        return true;
    }

    /** Wipes all data and seeds the demo roastery again. */
    @Transactional
    public void reset() {
        jdbc.execute("truncate table stock_movement, roast_lot, roast_event, roast_curve, roast, profile, lot, "
                + "location restart identity cascade");
        jdbc.execute("alter sequence roast_batch_seq restart with 1");
        seed();
    }

    private void seed() {
        Instant now = Instant.now(clock);
        LocationDto warehouse = inventory.createLocation(new LocationRequest("Main warehouse"));
        LocationDto shelf = inventory.createLocation(new LocationRequest("Roastery shelf"));

        Map<String, LotDto> lotByCode = new HashMap<>();
        for (LotSeed s : LOTS) {
            LotDto lot = inventory.createLot(new LotRequest(s.code(), s.name(), s.country(), s.region(), s.producer(),
                    s.variety(), s.process(), s.altitude(), s.cropYear(), s.supplier(), new BigDecimal(s.price()),
                    new BigDecimal(s.minKg()), "Synthetic demo lot"));
            lotByCode.put(s.code(), lot);
            inventory.receive(lot.id(), new ReceiptRequest(warehouse.id(), new BigDecimal(s.receivedKg()),
                    "Initial delivery", now.minus(Duration.ofDays(60)).truncatedTo(ChronoUnit.HOURS)));
        }
        // A second Brazil delivery, a stock count correction and a move to the roastery shelf.
        inventory.receive(lotByCode.get("BRA-CERR-25").id(), new ReceiptRequest(warehouse.id(), new BigDecimal("30"),
                "Top-up delivery", now.minus(Duration.ofDays(30)).truncatedTo(ChronoUnit.HOURS)));
        inventory.adjust(lotByCode.get("ETH-GUJI-25").id(), new AdjustmentRequest(warehouse.id(),
                new BigDecimal("-0.6"), "Stock count: moisture loss", now.minus(Duration.ofDays(35))));
        inventory.transfer(lotByCode.get("COL-HUILA-25").id(), new TransferRequest(warehouse.id(), shelf.id(),
                new BigDecimal("20")));

        Map<String, ProfileDto> profileByName = new HashMap<>();
        profileByName.put(FILTER, profiles.create(new ProfileRequest(FILTER,
                "Light roast for filter brewing: high sweetness, development time ratio around 13 %.")));
        profileByName.put(MEDIUM, profiles.create(new ProfileRequest(MEDIUM,
                "Medium roast for everyday brewing and milk drinks, development time ratio around 16 %.")));
        profileByName.put(ESPRESSO, profiles.create(new ProfileRequest(ESPRESSO,
                "Dark roast blend for espresso, development time ratio around 20 %.")));

        Map<String, Long> firstRoastOfProfile = new HashMap<>();
        Map<String, Long> referenceOfProfile = new HashMap<>();
        for (RoastSeed seed : roastSeeds()) {
            RoastDetailDto roast = createRoast(seed, now, profileByName.get(seed.profile()).id(), lotByCode);
            firstRoastOfProfile.putIfAbsent(seed.profile(), roast.summary().id());
            if (seed.jitter() <= 0.6) {
                referenceOfProfile.putIfAbsent(seed.profile(), roast.summary().id());
            }
        }
        for (Map.Entry<String, ProfileDto> e : profileByName.entrySet()) {
            Long reference = referenceOfProfile.getOrDefault(e.getKey(), firstRoastOfProfile.get(e.getKey()));
            profiles.setReference(e.getValue().id(), reference);
        }
        log.info("Seeded demo data: {} lots, {} roasts", LOTS.size(), roasts.count());
    }

    private RoastDetailDto createRoast(RoastSeed seed, Instant now, long profileId, Map<String, LotDto> lotByCode) {
        Spec spec = seed.spec().jitter(new Random(seed.spec().seed()), seed.jitter());
        GeneratedRoast generated = SyntheticRoastGenerator.generate(spec);
        Instant roastedAt = now.minus(Duration.ofDays(seed.daysAgo())).truncatedTo(ChronoUnit.DAYS)
                .plus(Duration.ofHours(seed.hour())).plusSeconds(seed.spec().seed() % 50 * 60L);
        Map<EventType, Double> events = generated.events();

        List<LotWeight> weights = new ArrayList<>();
        StringBuilder beans = new StringBuilder();
        seed.gramsByLot().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            LotDto lot = lotByCode.get(e.getKey());
            weights.add(new LotWeight(lot.id(), BigDecimal.valueOf(e.getValue())));
            if (!beans.isEmpty()) {
                beans.append(" + ");
            }
            beans.append(lot.name());
        });
        ParsedRoast parsed = new ParsedRoast(seed.name(), beans.toString(), MACHINE, roastedAt, generated.curve(),
                events, generated.greenWeightG(), generated.roastedWeightG(), SourceFormat.DEMO, List.of());
        return roastService.create(parsed, null, new ImportRequest(seed.name(), MACHINE, roastedAt, profileId,
                "Synthetic demo roast. The curve is generated, not recorded.", null, null, weights));
    }
}
