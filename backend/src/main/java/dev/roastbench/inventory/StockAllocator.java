package dev.roastbench.inventory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Decides where a roast takes its coffee from: first from the place that has the most stock. */
final class StockAllocator {

    private StockAllocator() {
    }

    /**
     * @return kilograms to take per location id, in the order they are used
     * @throws InsufficientStockException when the locations together hold less than {@code needed}
     */
    static Map<Long, BigDecimal> allocate(String lotCode, Map<Long, BigDecimal> stockByLocation, BigDecimal needed) {
        List<Map.Entry<Long, BigDecimal>> candidates = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> entry : stockByLocation.entrySet()) {
            if (entry.getValue().signum() > 0) {
                candidates.add(entry);
                total = total.add(entry.getValue());
            }
        }
        if (total.compareTo(needed) < 0) {
            throw new InsufficientStockException(lotCode, total, needed);
        }
        candidates.sort((a, b) -> {
            int byStock = b.getValue().compareTo(a.getValue());
            return byStock != 0 ? byStock : a.getKey().compareTo(b.getKey());
        });
        Map<Long, BigDecimal> allocation = new LinkedHashMap<>();
        BigDecimal remaining = needed;
        for (Map.Entry<Long, BigDecimal> candidate : candidates) {
            if (remaining.signum() == 0) {
                break;
            }
            BigDecimal take = candidate.getValue().min(remaining);
            allocation.put(candidate.getKey(), take);
            remaining = remaining.subtract(take);
        }
        return allocation;
    }
}
