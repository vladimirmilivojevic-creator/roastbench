package dev.roastbench.roast;

import dev.roastbench.analysis.AnalysisInput;
import dev.roastbench.analysis.ComparisonResult;
import dev.roastbench.analysis.CurveAnalyzer;
import dev.roastbench.analysis.EventType;
import dev.roastbench.analysis.PhaseMetrics;
import dev.roastbench.analysis.ReferenceComparator;
import dev.roastbench.analysis.RoastCurve;
import dev.roastbench.analysis.RoastMetrics;
import dev.roastbench.analysis.RorCalculator;
import dev.roastbench.common.AppProperties;
import dev.roastbench.common.ConflictException;
import dev.roastbench.common.NotFoundException;
import dev.roastbench.common.PageResponse;
import dev.roastbench.imports.ParsedRoast;
import dev.roastbench.imports.RoastLogImporter;
import dev.roastbench.inventory.InventoryService;
import dev.roastbench.inventory.Lot;
import dev.roastbench.inventory.LotRepository;
import dev.roastbench.profile.Profile;
import dev.roastbench.profile.ProfileRepository;
import dev.roastbench.roast.RoastDtos.CompareItemDto;
import dev.roastbench.roast.RoastDtos.CompareResponse;
import dev.roastbench.roast.RoastDtos.ComparisonDto;
import dev.roastbench.roast.RoastDtos.CurveDto;
import dev.roastbench.roast.RoastDtos.EventDto;
import dev.roastbench.roast.RoastDtos.ImportPreviewDto;
import dev.roastbench.roast.RoastDtos.ImportRequest;
import dev.roastbench.roast.RoastDtos.LotUsageDto;
import dev.roastbench.roast.RoastDtos.LotWeight;
import dev.roastbench.roast.RoastDtos.MetricsDto;
import dev.roastbench.roast.RoastDtos.PhaseDto;
import dev.roastbench.roast.RoastDtos.ProfileRef;
import dev.roastbench.roast.RoastDtos.RoastDetailDto;
import dev.roastbench.roast.RoastDtos.RoastSummaryDto;
import dev.roastbench.roast.RoastDtos.RoastUpdateRequest;
import dev.roastbench.roast.RoastParts.CurveData;
import dev.roastbench.roast.RoastParts.EventEntity;
import dev.roastbench.roast.RoastParts.RoastLotEntity;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Roast use cases: import, query, edit, compare and delete (which puts the green coffee back into stock). */
@Service
public class RoastService {

    static final int MAX_COMPARE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final BigDecimal WEIGHT_TOLERANCE_G = new BigDecimal("0.5");
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "roastedAt", "roastedAt", "batchNo", "batchNo", "name", "name", "dtr", "dtrPct", "loss", "lossPct",
            "totalTime", "totalTimeS", "dropTemp", "dropBt");
    private static final Set<EventType> EDITABLE_EVENTS = Set.of(EventType.TURNING_POINT, EventType.DRY_END,
            EventType.FIRST_CRACK_START, EventType.FIRST_CRACK_END, EventType.SECOND_CRACK_START,
            EventType.SECOND_CRACK_END, EventType.DROP);

    private final RoastRepository roasts;
    private final CurveRepository curves;
    private final EventRepository events;
    private final RoastLotRepository roastLots;
    private final ProfileRepository profiles;
    private final LotRepository lots;
    private final InventoryService inventory;
    private final RoastLogImporter importer;
    private final AppProperties properties;
    private final Clock clock;

    public RoastService(RoastRepository roasts, CurveRepository curves, EventRepository events,
                        RoastLotRepository roastLots, ProfileRepository profiles, LotRepository lots,
                        InventoryService inventory, RoastLogImporter importer, AppProperties properties, Clock clock) {
        this.roasts = roasts;
        this.curves = curves;
        this.events = events;
        this.roastLots = roastLots;
        this.profiles = profiles;
        this.lots = lots;
        this.inventory = inventory;
        this.importer = importer;
        this.properties = properties;
        this.clock = clock;
    }

    public record Filter(String q, Long profileId, Long lotId, Instant from, Instant to) {
    }

    // ---- import ---------------------------------------------------------------------------------------------

    /** Parses and analyses an upload without saving anything, so the user can check it before importing. */
    @Transactional(readOnly = true)
    public ImportPreviewDto preview(String filename, byte[] content) {
        ParsedRoast parsed = importer.importFile(filename, content);
        RoastMetrics metrics = CurveAnalyzer.analyze(new AnalysisInput(parsed.curve(), parsed.events(),
                parsed.greenWeightG(), parsed.roastedWeightG()));
        return new ImportPreviewDto(filename, parsed.format(), parsed.title(), parsed.beans(), parsed.machine(),
                parsed.roastedAt(), grams(parsed.greenWeightG()), grams(parsed.roastedWeightG()), parsed.warnings(),
                eventDtos(metrics, parsed.events(), parsed.curve()), curveDto(parsed.curve(),
                RorCalculator.DEFAULT_WINDOW_SECONDS), metricsDto(metrics));
    }

    @Transactional
    public RoastDetailDto importRoast(String filename, byte[] content, ImportRequest request) {
        return create(importer.importFile(filename, content), filename, request);
    }

    @Transactional
    public RoastDetailDto create(ParsedRoast parsed, String filename, ImportRequest request) {
        ImportRequest req = request != null ? request
                : new ImportRequest(null, null, null, null, null, null, null, null);
        if (roasts.count() >= properties.limits().maxRoasts()) {
            throw new ConflictException("The demo is limited to " + properties.limits().maxRoasts() + " roasts");
        }
        Profile profile = null;
        if (req.profileId() != null) {
            profile = profiles.findById(req.profileId()).orElseThrow(() -> new NotFoundException("Profile",
                    req.profileId()));
        }
        List<LotWeight> lotWeights = req.lots() == null ? List.of() : req.lots();
        BigDecimal green = resolveGreenWeight(req, parsed, lotWeights);
        BigDecimal roasted = req.roastedWeightG() != null ? req.roastedWeightG().setScale(1, RoundingMode.HALF_UP)
                : grams(parsed.roastedWeightG());

        RoastMetrics metrics = CurveAnalyzer.analyze(new AnalysisInput(parsed.curve(), parsed.events(),
                green == null ? null : green.doubleValue(), roasted == null ? null : roasted.doubleValue()));

        Instant roastedAt = req.roastedAt() != null ? req.roastedAt()
                : parsed.roastedAt() != null ? parsed.roastedAt() : Instant.now(clock);
        Roast roast = new Roast(roasts.nextBatchNo(), firstNonBlank(req.name(), parsed.title(), baseName(filename),
                "Roast"), roastedAt, parsed.format());
        roast.setMachine(blankToNull(firstNonBlank(req.machine(), parsed.machine(), null)));
        roast.setProfileId(profile == null ? null : profile.getId());
        roast.setGreenWeightG(green);
        roast.setRoastedWeightG(roasted);
        roast.setSourceFilename(filename);
        roast.setNotes(blankToNull(req.notes()));
        roast.applyMetrics(metrics);
        roasts.save(roast);

        RoastCurve curve = parsed.curve();
        curves.save(new CurveData(roast.getId(), curve.timeSeconds(), curve.beanTemp(), curve.envTemp()));
        List<EventEntity> eventRows = new ArrayList<>();
        parsed.events().forEach((type, time) -> eventRows.add(new EventEntity(roast.getId(), type, time)));
        events.saveAll(eventRows);

        List<LotWeight> ordered = lotWeights.stream().sorted(Comparator.comparing(LotWeight::lotId)).toList();
        for (LotWeight lw : ordered) {
            roastLots.save(new RoastLotEntity(roast.getId(), lw.lotId(), lw.greenWeightG().setScale(1,
                    RoundingMode.HALF_UP)));
            inventory.consumeForRoast(roast.getId(), lw.lotId(), lw.greenWeightG().divide(BigDecimal.valueOf(1000), 3,
                    RoundingMode.HALF_UP), roastedAt.isAfter(Instant.now(clock)) ? Instant.now(clock) : roastedAt);
        }
        return detail(roast.getId());
    }

    private static BigDecimal resolveGreenWeight(ImportRequest req, ParsedRoast parsed, List<LotWeight> lotWeights) {
        Set<Long> seen = new LinkedHashSet<>();
        for (LotWeight lw : lotWeights) {
            if (!seen.add(lw.lotId())) {
                throw new IllegalArgumentException("Lot " + lw.lotId() + " is listed twice");
            }
        }
        BigDecimal sum = lotWeights.stream().map(LotWeight::greenWeightG).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(1, RoundingMode.HALF_UP);
        BigDecimal stated = req.greenWeightG() != null ? req.greenWeightG().setScale(1, RoundingMode.HALF_UP)
                : grams(parsed.greenWeightG());
        if (lotWeights.isEmpty()) {
            return stated;
        }
        if (req.greenWeightG() != null && sum.subtract(stated).abs().compareTo(WEIGHT_TOLERANCE_G) > 0) {
            throw new IllegalArgumentException("The lot weights add up to " + sum + " g but the green weight is "
                    + stated + " g");
        }
        return sum;
    }

    // ---- queries --------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<RoastSummaryDto> list(Filter filter, int page, int size, String sort, String direction) {
        String field = SORT_FIELDS.getOrDefault(sort == null ? "roastedAt" : sort, null);
        if (field == null) {
            throw new IllegalArgumentException("Cannot sort by '" + sort + "'. Use one of " + SORT_FIELDS.keySet());
        }
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE),
                Sort.by(dir, field).and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Roast> result = roasts.findAll(specification(filter), pageable);
        return new PageResponse<>(summaries(result.getContent()), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public RoastDetailDto detail(long id) {
        Loaded loaded = load(id);
        return new RoastDetailDto(summaries(List.of(loaded.roast)).get(0), loaded.roast.getNotes(),
                loaded.roast.getSourceFilename(), eventDtos(loaded.metrics, loaded.marked, loaded.curve),
                metricsDto(loaded.metrics));
    }

    @Transactional(readOnly = true)
    public CurveDto curve(long id, double rorWindowSeconds) {
        return curveDto(load(id).curve, rorWindowSeconds);
    }

    @Transactional(readOnly = true)
    public ComparisonDto comparison(long id, Long referenceId, double rorWindowSeconds) {
        Loaded roast = load(id);
        Long refId = referenceId;
        if (refId == null) {
            Profile profile = roast.roast.getProfileId() == null ? null
                    : profiles.findById(roast.roast.getProfileId()).orElse(null);
            if (profile == null || profile.getReferenceRoastId() == null) {
                throw new ConflictException("This roast has no reference roast. Assign it to a profile that has a "
                        + "reference roast, or pass referenceId");
            }
            refId = profile.getReferenceRoastId();
        }
        Loaded reference = load(refId);
        ComparisonResult result = ReferenceComparator.compare(roast.curve, roast.metrics, reference.curve,
                reference.metrics);
        return new ComparisonDto(id, refId, rorWindowSeconds, result);
    }

    @Transactional(readOnly = true)
    public CompareResponse compareMany(List<Long> ids, double rorWindowSeconds) {
        List<Long> distinct = ids.stream().distinct().toList();
        if (distinct.isEmpty() || distinct.size() > MAX_COMPARE) {
            throw new IllegalArgumentException("Select between 1 and " + MAX_COMPARE + " roasts to compare");
        }
        List<Loaded> loaded = distinct.stream().map(this::load).toList();
        List<RoastSummaryDto> summaries = summaries(loaded.stream().map(l -> l.roast).toList());
        List<CompareItemDto> items = new ArrayList<>();
        for (int i = 0; i < loaded.size(); i++) {
            Loaded l = loaded.get(i);
            items.add(new CompareItemDto(summaries.get(i), curveDto(l.curve, rorWindowSeconds), metricsDto(l.metrics),
                    eventDtos(l.metrics, l.marked, l.curve)));
        }
        return new CompareResponse(items);
    }

    // ---- changes --------------------------------------------------------------------------------------------

    @Transactional
    public RoastDetailDto update(long id, RoastUpdateRequest request) {
        Roast roast = roasts.findById(id).orElseThrow(() -> new NotFoundException("Roast", id));
        if (request.name() != null) {
            roast.setName(request.name().trim());
        }
        if (request.machine() != null) {
            roast.setMachine(blankToNull(request.machine()));
        }
        if (request.notes() != null) {
            roast.setNotes(blankToNull(request.notes()));
        }
        if (request.roastedWeightG() != null) {
            roast.setRoastedWeightG(request.roastedWeightG().setScale(1, RoundingMode.HALF_UP));
        }
        Map<EventType, Double> marked = markedEvents(id);
        if (request.events() != null) {
            for (Map.Entry<EventType, Double> entry : request.events().entrySet()) {
                if (!EDITABLE_EVENTS.contains(entry.getKey())) {
                    throw new IllegalArgumentException(entry.getKey().label() + " cannot be edited");
                }
                if (entry.getValue() == null) {
                    marked.remove(entry.getKey());
                } else {
                    marked.put(entry.getKey(), entry.getValue());
                }
            }
        }
        RoastCurve curve = curveOf(id);
        RoastMetrics metrics = CurveAnalyzer.analyze(new AnalysisInput(curve, marked,
                roast.getGreenWeightG() == null ? null : roast.getGreenWeightG().doubleValue(),
                roast.getRoastedWeightG() == null ? null : roast.getRoastedWeightG().doubleValue()));
        roast.applyMetrics(metrics);
        if (request.events() != null) {
            events.deleteByRoastId(id);
            events.saveAll(marked.entrySet().stream().map(e -> new EventEntity(id, e.getKey(), e.getValue())).toList());
        }
        return detail(id);
    }

    @Transactional
    public void delete(long id) {
        Roast roast = roasts.findById(id).orElseThrow(() -> new NotFoundException("Roast", id));
        inventory.reverseRoast(id);
        roasts.delete(roast);
        roasts.flush();
    }

    // ---- loading and mapping --------------------------------------------------------------------------------

    private record Loaded(Roast roast, RoastCurve curve, Map<EventType, Double> marked, RoastMetrics metrics) {
    }

    private Loaded load(long id) {
        Roast roast = roasts.findById(id).orElseThrow(() -> new NotFoundException("Roast", id));
        RoastCurve curve = curveOf(id);
        Map<EventType, Double> marked = markedEvents(id);
        RoastMetrics metrics = CurveAnalyzer.analyze(new AnalysisInput(curve, marked,
                roast.getGreenWeightG() == null ? null : roast.getGreenWeightG().doubleValue(),
                roast.getRoastedWeightG() == null ? null : roast.getRoastedWeightG().doubleValue()));
        return new Loaded(roast, curve, marked, metrics);
    }

    private RoastCurve curveOf(long id) {
        CurveData data = curves.findById(id).orElseThrow(() -> new NotFoundException("Curve of roast", id));
        return new RoastCurve(data.getTimeSeconds(), data.getBeanTemp(), data.getEnvTemp());
    }

    private Map<EventType, Double> markedEvents(long id) {
        Map<EventType, Double> marked = new EnumMap<>(EventType.class);
        events.findByRoastId(id).forEach(e -> marked.put(e.getType(), e.getTimeSeconds()));
        return marked;
    }

    private List<RoastSummaryDto> summaries(List<Roast> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(Roast::getId).toList();
        Map<Long, Profile> profileById = profiles.findAllById(list.stream().map(Roast::getProfileId)
                .filter(java.util.Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(Profile::getId, p -> p));
        Map<Long, List<RoastLotEntity>> lotsByRoast = roastLots.findByRoastIds(ids).stream()
                .collect(Collectors.groupingBy(rl -> rl.getId().getRoastId()));
        Map<Long, Lot> lotById = lots.findAllById(lotsByRoast.values().stream().flatMap(List::stream)
                .map(rl -> rl.getId().getLotId()).distinct().toList()).stream()
                .collect(Collectors.toMap(Lot::getId, l -> l));

        List<RoastSummaryDto> result = new ArrayList<>();
        for (Roast r : list) {
            Profile p = r.getProfileId() == null ? null : profileById.get(r.getProfileId());
            List<LotUsageDto> usage = lotsByRoast.getOrDefault(r.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(rl -> rl.getId().getLotId()))
                    .map(rl -> {
                        Lot lot = lotById.get(rl.getId().getLotId());
                        return new LotUsageDto(lot.getId(), lot.getCode(), lot.getName(), lot.getCountry(),
                                rl.getGreenWeightG());
                    }).toList();
            result.add(new RoastSummaryDto(r.getId(), r.getBatchNo(), r.batchCode(), r.getName(), r.getRoastedAt(),
                    r.getMachine(), p == null ? null : new ProfileRef(p.getId(), p.getName()),
                    p != null && r.getId().equals(p.getReferenceRoastId()), r.getGreenWeightG(), r.getRoastedWeightG(),
                    r.getLossPct(), r.getTotalTimeS(), r.getDryEndS(), r.getFirstCrackS(), r.getDevTimeS(),
                    r.getDtrPct(), r.getDropBt(), r.getSourceFormat(), usage));
        }
        return result;
    }

    private static Specification<Roast> specification(Filter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("machine"), "")), like)));
            }
            if (f.profileId() != null) {
                predicates.add(cb.equal(root.get("profileId"), f.profileId()));
            }
            if (f.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("roastedAt"), f.from()));
            }
            if (f.to() != null) {
                predicates.add(cb.lessThan(root.<Instant>get("roastedAt"), f.to()));
            }
            if (f.lotId() != null) {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<RoastLotEntity> rl = sub.from(RoastLotEntity.class);
                sub.select(rl.get("id").<Long>get("roastId")).where(
                        cb.equal(rl.get("id").get("roastId"), root.get("id")),
                        cb.equal(rl.get("id").get("lotId"), f.lotId()));
                predicates.add(cb.exists(sub));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static CurveDto curveDto(RoastCurve curve, double rorWindowSeconds) {
        double[] t = curve.timeSeconds();
        double[] bt = curve.beanTemp();
        double[] ror = RorCalculator.compute(t, bt, rorWindowSeconds);
        for (int i = 0; i < ror.length; i++) {
            ror[i] = Math.round(ror[i] * 100.0) / 100.0;
        }
        return new CurveDto(rorWindowSeconds, t, bt, curve.envTemp(), ror);
    }

    private static List<EventDto> eventDtos(RoastMetrics metrics, Map<EventType, Double> marked, RoastCurve curve) {
        List<EventDto> list = new ArrayList<>();
        list.add(new EventDto(EventType.CHARGE, EventType.CHARGE.label(), 0.0, curve.beanTempAt(0.0), false));
        metrics.events().forEach((type, time) -> list.add(new EventDto(type, type.label(), time,
                curve.beanTempAt(time), !marked.containsKey(type))));
        list.sort(Comparator.comparingDouble(EventDto::timeSeconds));
        return list;
    }

    private static MetricsDto metricsDto(RoastMetrics m) {
        List<PhaseDto> phases = m.phases().stream().map(RoastService::phaseDto).toList();
        return new MetricsDto(m.totalTimeSeconds(), m.dropEstimated(), m.chargeBeanTemp(), m.dropBeanTemp(),
                m.turningPointSeconds(), m.turningPointBeanTemp(), m.averageRorAfterTurningPoint(), phases,
                m.developmentTimeRatioPercent(), m.roastLossPercent());
    }

    private static PhaseDto phaseDto(PhaseMetrics p) {
        return new PhaseDto(p.phase().name(), p.startSeconds(), p.endSeconds(), p.durationSeconds(),
                p.percentOfTotal(), p.startBeanTemp(), p.endBeanTemp(), p.averageRor(), p.estimated());
    }

    private static BigDecimal grams(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String baseName(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        String name = filename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
