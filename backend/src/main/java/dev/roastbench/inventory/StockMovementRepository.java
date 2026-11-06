package dev.roastbench.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    interface LotStock {
        Long getLotId();

        BigDecimal getStockKg();
    }

    interface LocationStock {
        Long getLocationId();

        BigDecimal getStockKg();
    }

    /** Booking order. The running balance only makes sense in this order, whatever the movement dates say. */
    List<StockMovement> findByLotIdOrderByIdAsc(Long lotId);

    List<StockMovement> findByRoastIdAndType(Long roastId, MovementType type);

    @Query("select m.locationId as locationId, sum(m.quantityKg) as stockKg from StockMovement m "
            + "where m.lotId = :lotId group by m.locationId")
    List<LocationStock> stockByLocation(@Param("lotId") Long lotId);

    @Query("select m.lotId as lotId, sum(m.quantityKg) as stockKg from StockMovement m group by m.lotId")
    List<LotStock> stockByLot();

    @Query("select m.lotId as lotId, sum(m.quantityKg) as stockKg from StockMovement m "
            + "where m.type in :types and m.occurredAt >= :since group by m.lotId")
    List<LotStock> netByLotSince(@Param("types") Collection<MovementType> types, @Param("since") Instant since);
}
