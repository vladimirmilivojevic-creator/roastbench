package dev.roastbench.roast;

import dev.roastbench.inventory.InventoryDtos.AlertDto;
import dev.roastbench.inventory.InventoryDtos.SummaryDto;
import dev.roastbench.inventory.InventoryService;
import dev.roastbench.roast.RoastDtos.RoastSummaryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Numbers for the start page")
public class DashboardController {

    public record DashboardDto(long totalRoasts, long roastsLast30Days, Double avgDtrPercentLast30Days,
                               Double avgLossPercentLast30Days, BigDecimal kgRoastedLast30Days,
                               SummaryDto inventory, List<AlertDto> alerts, List<RoastSummaryDto> recentRoasts) {
    }

    private final RoastRepository roasts;
    private final RoastService roastService;
    private final InventoryService inventory;
    private final Clock clock;

    public DashboardController(RoastRepository roasts, RoastService roastService, InventoryService inventory,
                               Clock clock) {
        this.roasts = roasts;
        this.roastService = roastService;
        this.inventory = inventory;
        this.clock = clock;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Roast statistics of the last 30 days, stock summary, low-stock alerts, latest roasts")
    public DashboardDto dashboard() {
        RoastRepository.WindowStats stats = roasts.windowStats(Instant.now(clock).minus(Duration.ofDays(30)));
        BigDecimal kg = stats.getGreenG() == null ? BigDecimal.ZERO
                : stats.getGreenG().divide(BigDecimal.valueOf(1000), 1, RoundingMode.HALF_UP);
        return new DashboardDto(roasts.count(), stats.getRoastCount(), round(stats.getAvgDtr()),
                round(stats.getAvgLoss()), kg, inventory.summary(), inventory.alerts(),
                roastService.list(new RoastService.Filter(null, null, null, null, null), 0, 5, "roastedAt", "desc")
                        .items());
    }

    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 100.0) / 100.0;
    }
}
