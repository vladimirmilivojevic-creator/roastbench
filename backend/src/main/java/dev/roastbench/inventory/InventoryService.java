package dev.roastbench.inventory;

import dev.roastbench.common.AppProperties;
import dev.roastbench.common.ConflictException;
import dev.roastbench.common.NotFoundException;
import dev.roastbench.inventory.InventoryDtos.AdjustmentRequest;
import dev.roastbench.inventory.InventoryDtos.AlertDto;
import dev.roastbench.inventory.InventoryDtos.CountryStockDto;
import dev.roastbench.inventory.InventoryDtos.LocationDto;
import dev.roastbench.inventory.InventoryDtos.LocationRequest;
import dev.roastbench.inventory.InventoryDtos.LotDto;
import dev.roastbench.inventory.InventoryDtos.LotRequest;
import dev.roastbench.inventory.InventoryDtos.MovementDto;
import dev.roastbench.inventory.InventoryDtos.ReceiptRequest;
import dev.roastbench.inventory.InventoryDtos.StockByLocationDto;
import dev.roastbench.inventory.InventoryDtos.StockStatus;
import dev.roastbench.inventory.InventoryDtos.SummaryDto;
import dev.roastbench.inventory.InventoryDtos.TransferRequest;
import dev.roastbench.inventory.StockMovementRepository.LocationStock;
import dev.roastbench.inventory.StockMovementRepository.LotStock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Green coffee inventory: lots, locations and the append-only stock ledger.
 *
 * <p>Stock is never stored: it is the sum of the ledger. Every posting locks the lot row first, so two concurrent
 * roasts cannot both spend the same kilograms.
 */
@Service
public class InventoryService {

    private static final Set<MovementType> USAGE_TYPES = Set.of(MovementType.ROAST_CONSUMPTION, MovementType.ROAST_REVERSAL);
    static final int PROJECTION_DAYS = 30;

    private final LotRepository lots;
    private final LocationRepository locations;
    private final StockMovementRepository movements;
    private final AppProperties properties;
    private final Clock clock;

    public InventoryService(LotRepository lots, LocationRepository locations, StockMovementRepository movements,
                            AppProperties properties, Clock clock) {
        this.lots = lots;
        this.locations = locations;
        this.movements = movements;
        this.properties = properties;
        this.clock = clock;
    }

    // ---- lots -----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<LotDto> listLots(String query, String country, StockStatus status, boolean includeArchived) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return lotViews().stream()
                .filter(l -> includeArchived || !l.archived())
                .filter(l -> q.isEmpty() || contains(l.code(), q) || contains(l.name(), q) || contains(l.country(), q)
                        || contains(l.region(), q) || contains(l.producer(), q) || contains(l.variety(), q))
                .filter(l -> country == null || country.isBlank() || l.country().equalsIgnoreCase(country.trim()))
                .filter(l -> status == null || l.status() == status)
                .sorted(Comparator.comparing(LotDto::code, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public LotDto getLot(long id) {
        Lot lot = lot(id);
        return toDto(lot, stockOf(id), usageOf(id));
    }

    @Transactional
    public LotDto createLot(LotRequest request) {
        if (lots.count() >= properties.limits().maxLots()) {
            throw new ConflictException("The demo is limited to " + properties.limits().maxLots() + " lots");
        }
        String code = request.code().trim();
        if (lots.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("A lot with code " + code + " already exists");
        }
        Lot lot = new Lot(code, request.name().trim(), request.country().trim());
        apply(lot, request);
        lots.save(lot);
        return toDto(lot, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Transactional
    public LotDto updateLot(long id, LotRequest request) {
        Lot lot = lot(id);
        String code = request.code().trim();
        if (!lot.getCode().equalsIgnoreCase(code) && lots.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("A lot with code " + code + " already exists");
        }
        lot.setCode(code);
        lot.setName(request.name().trim());
        lot.setCountry(request.country().trim());
        apply(lot, request);
        return toDto(lot, stockOf(id), usageOf(id));
    }

    @Transactional
    public LotDto setArchived(long id, boolean archived) {
        Lot lot = lot(id);
        lot.setArchived(archived);
        return toDto(lot, stockOf(id), usageOf(id));
    }

    // ---- locations ------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<LocationDto> listLocations() {
        return locations.findAll().stream()
                .sorted(Comparator.comparing(Location::getName, String.CASE_INSENSITIVE_ORDER))
                .map(l -> new LocationDto(l.getId(), l.getName())).toList();
    }

    @Transactional
    public LocationDto createLocation(LocationRequest request) {
        String name = request.name().trim();
        if (locations.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A location named " + name + " already exists");
        }
        Location location = locations.save(new Location(name));
        return new LocationDto(location.getId(), location.getName());
    }

    // ---- ledger ---------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<StockByLocationDto> stockByLocation(long lotId) {
        lot(lotId);
        Map<Long, String> names = locationNames();
        return movements.stockByLocation(lotId).stream()
                .filter(s -> s.getStockKg().signum() != 0)
                .map(s -> new StockByLocationDto(s.getLocationId(), names.get(s.getLocationId()), s.getStockKg()))
                .sorted(Comparator.comparing(StockByLocationDto::stockKg).reversed())
                .toList();
    }

    /**
     * The ledger of one lot, newest booking first, each row with the stock balance after it. Rows are ordered by when
     * they were booked; {@code occurredAt} is when the event happened (a roast imported today may be from last week).
     */
    @Transactional(readOnly = true)
    public List<MovementDto> movementsOf(long lotId) {
        lot(lotId);
        Map<Long, String> names = locationNames();
        List<MovementDto> result = new ArrayList<>();
        BigDecimal balance = BigDecimal.ZERO;
        for (StockMovement m : movements.findByLotIdOrderByIdAsc(lotId)) {
            balance = balance.add(m.getQuantityKg());
            result.add(new MovementDto(m.getId(), m.getLotId(), m.getLocationId(), names.get(m.getLocationId()),
                    m.getType(), m.getType().label(), m.getQuantityKg(), balance, m.getRoastId(), m.getReason(),
                    m.getOccurredAt()));
        }
        Collections.reverse(result);
        return result;
    }

    @Transactional
    public LotDto receive(long lotId, ReceiptRequest request) {
        Lot lot = lockActiveLot(lotId);
        location(request.locationId());
        post(lot.getId(), request.locationId(), MovementType.RECEIPT, scale(request.quantityKg()), null,
                request.reason(), request.occurredAt());
        return toDto(lot, stockOf(lotId), usageOf(lotId));
    }

    @Transactional
    public LotDto adjust(long lotId, AdjustmentRequest request) {
        BigDecimal delta = scale(request.deltaKg());
        if (delta.signum() == 0) {
            throw new IllegalArgumentException("The adjustment must not be zero");
        }
        Lot lot = lockActiveLot(lotId);
        location(request.locationId());
        BigDecimal atLocation = locationStock(lotId, request.locationId());
        if (atLocation.add(delta).signum() < 0) {
            throw new ConflictException("The location holds only " + plain(atLocation) + " kg of lot " + lot.getCode());
        }
        post(lotId, request.locationId(), MovementType.ADJUSTMENT, delta, null, request.reason(), request.occurredAt());
        return toDto(lot, stockOf(lotId), usageOf(lotId));
    }

    @Transactional
    public LotDto transfer(long lotId, TransferRequest request) {
        if (request.fromLocationId().equals(request.toLocationId())) {
            throw new IllegalArgumentException("Source and destination location must differ");
        }
        Lot lot = lockActiveLot(lotId);
        Location from = location(request.fromLocationId());
        Location to = location(request.toLocationId());
        BigDecimal quantity = scale(request.quantityKg());
        BigDecimal atSource = locationStock(lotId, from.getId());
        if (atSource.compareTo(quantity) < 0) {
            throw new InsufficientStockException(lot.getCode() + " at " + from.getName(), atSource, quantity);
        }
        String reason = "Transfer " + from.getName() + " -> " + to.getName();
        post(lotId, from.getId(), MovementType.TRANSFER_OUT, quantity.negate(), null, reason, null);
        post(lotId, to.getId(), MovementType.TRANSFER_IN, quantity, null, reason, null);
        return toDto(lot, stockOf(lotId), usageOf(lotId));
    }

    /**
     * Books green coffee used by a roast, taking it from the locations with the most stock first.
     * Must run in the roast's transaction so a failure rolls the whole roast back.
     */
    @Transactional
    public void consumeForRoast(long roastId, String roastLabel, long lotId, BigDecimal kilograms, Instant at) {
        Lot lot = lockActiveLot(lotId);
        BigDecimal needed = scale(kilograms);
        Map<Long, BigDecimal> stock = new HashMap<>();
        for (LocationStock s : movements.stockByLocation(lotId)) {
            stock.put(s.getLocationId(), s.getStockKg());
        }
        Map<Long, BigDecimal> allocation = StockAllocator.allocate(lot.getCode(), stock, needed);
        String reason = "Roast " + roastLabel;
        allocation.forEach((locationId, kg) ->
                post(lotId, locationId, MovementType.ROAST_CONSUMPTION, kg.negate(), roastId, reason, at));
    }

    /** Puts the green coffee of a roast that is being deleted back into stock. */
    @Transactional
    public void reverseRoast(long roastId, String roastLabel) {
        List<StockMovement> consumed = movements.findByRoastIdAndType(roastId, MovementType.ROAST_CONSUMPTION);
        for (Long lotId : new TreeSet<>(consumed.stream().map(StockMovement::getLotId).toList())) {
            lots.findByIdForUpdate(lotId); // consistent lock order: ascending lot id
        }
        for (StockMovement m : consumed) {
            post(m.getLotId(), m.getLocationId(), MovementType.ROAST_REVERSAL, m.getQuantityKg().negate(), roastId,
                    "Roast " + roastLabel + " deleted", null);
        }
    }

    // ---- reports --------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AlertDto> alerts() {
        return lotViews().stream()
                .filter(l -> !l.archived() && l.minStockKg().signum() > 0 && l.stockKg().compareTo(l.minStockKg()) < 0)
                .map(l -> new AlertDto(l.id(), l.code(), l.name(), l.country(), l.stockKg(), l.minStockKg(),
                        l.stockKg().signum() <= 0 ? StockStatus.OUT : StockStatus.LOW, l.daysOfStockLeft()))
                .sorted(Comparator.comparing((AlertDto a) -> a.severity() == StockStatus.OUT ? 0 : 1)
                        .thenComparing(a -> a.stockKg().divide(a.minStockKg(), 6, RoundingMode.HALF_UP)))
                .toList();
    }

    @Transactional(readOnly = true)
    public SummaryDto summary() {
        List<LotDto> active = lotViews().stream().filter(l -> !l.archived()).toList();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal value = BigDecimal.ZERO;
        int low = 0;
        int out = 0;
        Map<String, BigDecimal> byCountry = new LinkedHashMap<>();
        for (LotDto l : active) {
            BigDecimal stock = l.stockKg().max(BigDecimal.ZERO);
            total = total.add(stock);
            if (l.pricePerKg() != null) {
                value = value.add(stock.multiply(l.pricePerKg()));
            }
            if (l.status() == StockStatus.LOW) {
                low++;
            } else if (l.status() == StockStatus.OUT) {
                out++;
            }
            byCountry.merge(l.country(), stock, BigDecimal::add);
        }
        List<CountryStockDto> countries = byCountry.entrySet().stream()
                .map(e -> new CountryStockDto(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(CountryStockDto::stockKg).reversed()).toList();
        return new SummaryDto(total, active.size(), low, out, value.setScale(2, RoundingMode.HALF_UP), countries);
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    private List<LotDto> lotViews() {
        Map<Long, BigDecimal> stock = new HashMap<>();
        for (LotStock s : movements.stockByLot()) {
            stock.put(s.getLotId(), s.getStockKg());
        }
        Map<Long, BigDecimal> usage = new HashMap<>();
        for (LotStock s : movements.netByLotSince(USAGE_TYPES, since())) {
            usage.put(s.getLotId(), s.getStockKg().negate());
        }
        return lots.findAll().stream()
                .map(l -> toDto(l, stock.getOrDefault(l.getId(), BigDecimal.ZERO),
                        usage.getOrDefault(l.getId(), BigDecimal.ZERO)))
                .toList();
    }

    private LotDto toDto(Lot lot, BigDecimal stock, BigDecimal usedInWindow) {
        StockStatus status;
        if (stock.signum() <= 0) {
            status = StockStatus.OUT;
        } else if (lot.getMinStockKg().signum() > 0 && stock.compareTo(lot.getMinStockKg()) < 0) {
            status = StockStatus.LOW;
        } else {
            status = StockStatus.OK;
        }
        BigDecimal avgDaily = usedInWindow.signum() > 0
                ? usedInWindow.divide(BigDecimal.valueOf(PROJECTION_DAYS), 3, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        Long daysLeft = null;
        if (avgDaily.signum() > 0 && stock.signum() > 0) {
            daysLeft = stock.divide(avgDaily, 0, RoundingMode.FLOOR).longValue();
        }
        return new LotDto(lot.getId(), lot.getCode(), lot.getName(), lot.getCountry(), lot.getRegion(),
                lot.getProducer(), lot.getVariety(), lot.getProcess(), lot.getAltitudeM(), lot.getCropYear(),
                lot.getSupplier(), lot.getPricePerKg(), lot.getMinStockKg(), lot.getNotes(), lot.isArchived(), stock,
                status, avgDaily, daysLeft, lot.getCreatedAt());
    }

    private static void apply(Lot lot, LotRequest r) {
        lot.setRegion(blankToNull(r.region()));
        lot.setProducer(blankToNull(r.producer()));
        lot.setVariety(blankToNull(r.variety()));
        lot.setProcess(blankToNull(r.process()));
        lot.setAltitudeM(r.altitudeM());
        lot.setCropYear(r.cropYear());
        lot.setSupplier(blankToNull(r.supplier()));
        lot.setPricePerKg(r.pricePerKg() == null ? null : r.pricePerKg().setScale(2, RoundingMode.HALF_UP));
        lot.setMinStockKg(r.minStockKg() == null ? BigDecimal.ZERO : scale(r.minStockKg()));
        lot.setNotes(blankToNull(r.notes()));
    }

    private void post(long lotId, long locationId, MovementType type, BigDecimal quantityKg, Long roastId,
                      String reason, Instant occurredAt) {
        movements.save(new StockMovement(lotId, locationId, type, quantityKg, roastId, blankToNull(reason),
                occurredAt != null ? occurredAt : Instant.now(clock)));
    }

    private Instant since() {
        return Instant.now(clock).minus(Duration.ofDays(PROJECTION_DAYS));
    }

    private BigDecimal stockOf(long lotId) {
        return movements.stockByLocation(lotId).stream().map(LocationStock::getStockKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal usageOf(long lotId) {
        return movements.netByLotSince(USAGE_TYPES, since()).stream()
                .filter(s -> s.getLotId().equals(lotId)).map(s -> s.getStockKg().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal locationStock(long lotId, long locationId) {
        return movements.stockByLocation(lotId).stream().filter(s -> s.getLocationId().equals(locationId))
                .map(LocationStock::getStockKg).findFirst().orElse(BigDecimal.ZERO);
    }

    private Map<Long, String> locationNames() {
        Map<Long, String> names = new HashMap<>();
        locations.findAll().forEach(l -> names.put(l.getId(), l.getName()));
        return names;
    }

    private Lot lot(long id) {
        return lots.findById(id).orElseThrow(() -> new NotFoundException("Lot", id));
    }

    private Lot lockActiveLot(long id) {
        Lot lot = lots.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Lot", id));
        if (lot.isArchived()) {
            throw new ConflictException("Lot " + lot.getCode() + " is archived");
        }
        return lot;
    }

    private Location location(long id) {
        return locations.findById(id).orElseThrow(() -> new NotFoundException("Location", id));
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static boolean contains(String value, String lowerQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerQuery);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
