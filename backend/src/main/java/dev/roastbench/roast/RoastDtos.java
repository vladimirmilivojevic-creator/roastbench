package dev.roastbench.roast;

import dev.roastbench.analysis.ComparisonResult;
import dev.roastbench.analysis.EventType;
import dev.roastbench.imports.SourceFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request and response shapes of the roast API. */
public final class RoastDtos {

    private RoastDtos() {
    }

    public record ProfileRef(Long id, String name) {
    }

    public record LotUsageDto(Long lotId, String code, String name, String country, BigDecimal greenWeightG) {
    }

    public record RoastSummaryDto(Long id, long batchNo, String batchCode, String name, Instant roastedAt,
                                  String machine, ProfileRef profile, boolean referenceOfProfile,
                                  BigDecimal greenWeightG, BigDecimal roastedWeightG, Double lossPercent,
                                  double totalTimeSeconds, Double dryEndSeconds, Double firstCrackSeconds,
                                  Double developmentSeconds, Double dtrPercent, double dropBeanTemp,
                                  SourceFormat sourceFormat, List<LotUsageDto> lots) {
    }

    public record EventDto(EventType type, String label, double timeSeconds, Double beanTemp, boolean estimated) {
    }

    public record PhaseDto(String phase, double startSeconds, double endSeconds, double durationSeconds,
                           double percentOfTotal, double startBeanTemp, double endBeanTemp, double averageRor,
                           boolean estimated) {
    }

    public record MetricsDto(double totalTimeSeconds, boolean dropEstimated, double chargeBeanTemp,
                             double dropBeanTemp, double turningPointSeconds, double turningPointBeanTemp,
                             double averageRorAfterTurningPoint, List<PhaseDto> phases,
                             Double developmentTimeRatioPercent, Double roastLossPercent) {
    }

    public record RoastDetailDto(RoastSummaryDto summary, String notes, String sourceFilename, List<EventDto> events,
                                 MetricsDto metrics) {
    }

    /** Samples of a roast with the rate of rise (degrees Celsius per minute) computed for the given window. */
    public record CurveDto(double rorWindowSeconds, double[] timeSeconds, double[] beanTemp, double[] envTemp,
                           double[] ror) {
    }

    public record CompareItemDto(RoastSummaryDto roast, CurveDto curve, MetricsDto metrics, List<EventDto> events) {
    }

    public record CompareResponse(List<CompareItemDto> items) {
    }

    public record ComparisonDto(Long roastId, Long referenceId, double rorWindowSeconds, ComparisonResult result) {
    }

    public record ImportPreviewDto(String filename, SourceFormat format, String title, String beans, String machine,
                                   Instant roastedAt, BigDecimal greenWeightG, BigDecimal roastedWeightG,
                                   List<String> warnings, List<EventDto> events, CurveDto curve,
                                   MetricsDto metrics) {
    }

    public record LotWeight(@NotNull Long lotId,
                            @NotNull @DecimalMin(value = "0.1", message = "must be at least 0.1 g")
                            @DecimalMax("1000000") BigDecimal greenWeightG) {
    }

    /** What the user decides in the import wizard on top of what the file says. All fields are optional. */
    public record ImportRequest(@Size(max = 150) String name, @Size(max = 120) String machine, Instant roastedAt,
                                Long profileId, @Size(max = 4000) String notes,
                                @DecimalMin("0.1") @DecimalMax("1000000") BigDecimal greenWeightG,
                                @DecimalMin("0.1") @DecimalMax("1000000") BigDecimal roastedWeightG,
                                @Valid @Size(max = 10) List<LotWeight> lots) {
    }

    /** Partial update: null fields stay unchanged. Event times of {@code null} remove the event. */
    public record RoastUpdateRequest(@Size(min = 1, max = 150) String name, @Size(max = 120) String machine,
                                     @Size(max = 4000) String notes,
                                     @DecimalMin("0.1") @DecimalMax("1000000") BigDecimal roastedWeightG,
                                     Map<EventType, Double> events) {
    }

    public record AssignProfileRequest(Long profileId) {
    }
}
