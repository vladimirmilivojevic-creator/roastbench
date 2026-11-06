package dev.roastbench.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StockAllocatorTest {

    private static BigDecimal kg(String v) {
        return new BigDecimal(v);
    }

    @Test
    void takesEverythingFromTheLargestLocationFirst() {
        Map<Long, BigDecimal> allocation = StockAllocator.allocate("LOT", Map.of(1L, kg("5"), 2L, kg("20")), kg("8"));

        assertThat(allocation).containsOnlyKeys(2L);
        assertThat(allocation.get(2L)).isEqualByComparingTo("8");
    }

    @Test
    void spillsOverToTheNextLocationWhenTheLargestIsNotEnough() {
        Map<Long, BigDecimal> allocation = StockAllocator.allocate("LOT",
                Map.of(1L, kg("5"), 2L, kg("20"), 3L, kg("3")), kg("22"));

        assertThat(allocation.keySet()).containsExactly(2L, 1L);
        assertThat(allocation.get(2L)).isEqualByComparingTo("20");
        assertThat(allocation.get(1L)).isEqualByComparingTo("2");
    }

    @Test
    void breaksTiesByLocationId() {
        Map<Long, BigDecimal> allocation = StockAllocator.allocate("LOT", Map.of(7L, kg("10"), 3L, kg("10")), kg("4"));

        assertThat(allocation).containsOnlyKeys(3L);
    }

    @Test
    void usesTheWholeStockWhenAskedForExactlyThat() {
        Map<Long, BigDecimal> allocation = StockAllocator.allocate("LOT", Map.of(1L, kg("5"), 2L, kg("2.5")), kg("7.5"));

        assertThat(allocation.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("7.5");
    }

    @Test
    void ignoresEmptyAndNegativeLocations() {
        Map<Long, BigDecimal> allocation = StockAllocator.allocate("LOT",
                Map.of(1L, kg("0"), 2L, kg("-1"), 3L, kg("4")), kg("4"));

        assertThat(allocation).containsOnlyKeys(3L);
    }

    @Test
    void refusesWhenThereIsNotEnoughStock() {
        assertThatThrownBy(() -> StockAllocator.allocate("ETH-1", Map.of(1L, kg("3"), 2L, kg("1")), kg("5")))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Not enough stock of lot ETH-1: 4 kg available, 5 kg needed");
    }
}
