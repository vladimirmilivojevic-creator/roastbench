package dev.roastbench.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** One row of the append-only stock ledger. Rows are never updated or deleted. */
@Entity
@Table(name = "stock_movement")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lot_id", nullable = false, updatable = false)
    private Long lotId;

    @Column(name = "location_id", nullable = false, updatable = false)
    private Long locationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private MovementType type;

    @Column(name = "quantity_kg", nullable = false, updatable = false, precision = 10, scale = 3)
    private BigDecimal quantityKg;

    @Column(name = "roast_id", updatable = false)
    private Long roastId;

    @Column(updatable = false, length = 300)
    private String reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected StockMovement() {
    }

    public StockMovement(Long lotId, Long locationId, MovementType type, BigDecimal quantityKg, Long roastId,
                         String reason, Instant occurredAt) {
        this.lotId = lotId;
        this.locationId = locationId;
        this.type = type;
        this.quantityKg = quantityKg;
        this.roastId = roastId;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public Long getLotId() {
        return lotId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public MovementType getType() {
        return type;
    }

    public BigDecimal getQuantityKg() {
        return quantityKg;
    }

    public Long getRoastId() {
        return roastId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
