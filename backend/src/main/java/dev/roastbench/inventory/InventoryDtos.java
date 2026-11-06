package dev.roastbench.inventory;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Request and response shapes of the inventory API. */
public final class InventoryDtos {

    private InventoryDtos() {
    }

    public enum StockStatus {
        /** Stock is at or above the minimum level. */
        OK,
        /** Stock is below the minimum level. */
        LOW,
        /** Nothing left. */
        OUT
    }

    public record LotRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 80) String country,
            @Size(max = 120) String region,
            @Size(max = 150) String producer,
            @Size(max = 120) String variety,
            @Size(max = 80) String process,
            @Min(0) @Max(6000) Integer altitudeM,
            @Min(1990) @Max(2100) Integer cropYear,
            @Size(max = 150) String supplier,
            @DecimalMin("0") @DecimalMax("100000") BigDecimal pricePerKg,
            @DecimalMin("0") @DecimalMax("1000000") BigDecimal minStockKg,
            @Size(max = 2000) String notes) {
    }

    public record LotDto(Long id, String code, String name, String country, String region, String producer,
                         String variety, String process, Integer altitudeM, Integer cropYear, String supplier,
                         BigDecimal pricePerKg, BigDecimal minStockKg, String notes, boolean archived,
                         BigDecimal stockKg, StockStatus status, BigDecimal avgDailyUseKg, Long daysOfStockLeft,
                         Instant createdAt) {
    }

    public record LocationRequest(@NotBlank @Size(max = 100) String name) {
    }

    public record LocationDto(Long id, String name) {
    }

    public record StockByLocationDto(Long locationId, String locationName, BigDecimal stockKg) {
    }

    public record ReceiptRequest(
            @NotNull Long locationId,
            @NotNull @DecimalMin(value = "0.001", message = "must be greater than 0") @DecimalMax("1000000")
            BigDecimal quantityKg,
            @Size(max = 300) String reason,
            Instant occurredAt) {
    }

    public record AdjustmentRequest(
            @NotNull Long locationId,
            @NotNull @DecimalMin("-1000000") @DecimalMax("1000000") BigDecimal deltaKg,
            @NotBlank @Size(max = 300) String reason,
            Instant occurredAt) {
    }

    public record TransferRequest(
            @NotNull Long fromLocationId,
            @NotNull Long toLocationId,
            @NotNull @DecimalMin(value = "0.001", message = "must be greater than 0") @DecimalMax("1000000")
            BigDecimal quantityKg) {
    }

    public record MovementDto(Long id, Long lotId, Long locationId, String locationName, MovementType type,
                              String typeLabel, BigDecimal quantityKg, BigDecimal balanceKg, Long roastId,
                              String reason, Instant occurredAt) {
    }

    public record AlertDto(Long lotId, String code, String name, String country, BigDecimal stockKg,
                           BigDecimal minStockKg, StockStatus severity, Long daysOfStockLeft) {
    }

    public record CountryStockDto(String country, BigDecimal stockKg) {
    }

    public record SummaryDto(BigDecimal totalStockKg, int lotCount, int lowStockCount, int outOfStockCount,
                             BigDecimal stockValue, List<CountryStockDto> byCountry) {
    }
}
