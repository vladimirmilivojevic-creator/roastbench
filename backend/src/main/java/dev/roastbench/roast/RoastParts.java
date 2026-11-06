package dev.roastbench.roast;

import dev.roastbench.analysis.EventType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The satellite tables of a roast: curve samples, marked events and green coffee lots used. */
public final class RoastParts {

    private RoastParts() {
    }

    /** Curve samples; time in seconds since charge, temperatures in degrees Celsius. */
    @Entity(name = "RoastCurveData")
    @Table(name = "roast_curve")
    public static class CurveData {

        @Id
        @Column(name = "roast_id")
        private Long roastId;

        @JdbcTypeCode(SqlTypes.ARRAY)
        @Column(name = "time_s", nullable = false, columnDefinition = "double precision[]")
        private double[] timeSeconds;

        @JdbcTypeCode(SqlTypes.ARRAY)
        @Column(name = "bt", nullable = false, columnDefinition = "double precision[]")
        private double[] beanTemp;

        @JdbcTypeCode(SqlTypes.ARRAY)
        @Column(name = "et", columnDefinition = "double precision[]")
        private double[] envTemp;

        protected CurveData() {
        }

        public CurveData(Long roastId, double[] timeSeconds, double[] beanTemp, double[] envTemp) {
            this.roastId = roastId;
            this.timeSeconds = timeSeconds;
            this.beanTemp = beanTemp;
            this.envTemp = envTemp;
        }

        public Long getRoastId() {
            return roastId;
        }

        public double[] getTimeSeconds() {
            return timeSeconds;
        }

        public double[] getBeanTemp() {
            return beanTemp;
        }

        public double[] getEnvTemp() {
            return envTemp;
        }
    }

    /** An event marked by the roaster or the logger. At most one per type and roast. */
    @Entity(name = "RoastEvent")
    @Table(name = "roast_event")
    public static class EventEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "roast_id", nullable = false)
        private Long roastId;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 30)
        private EventType type;

        @Column(name = "time_s", nullable = false)
        private double timeSeconds;

        protected EventEntity() {
        }

        public EventEntity(Long roastId, EventType type, double timeSeconds) {
            this.roastId = roastId;
            this.type = type;
            this.timeSeconds = timeSeconds;
        }

        public Long getRoastId() {
            return roastId;
        }

        public EventType getType() {
            return type;
        }

        public double getTimeSeconds() {
            return timeSeconds;
        }
    }

    @Embeddable
    public static class RoastLotId implements Serializable {

        @Column(name = "roast_id")
        private Long roastId;

        @Column(name = "lot_id")
        private Long lotId;

        protected RoastLotId() {
        }

        public RoastLotId(Long roastId, Long lotId) {
            this.roastId = roastId;
            this.lotId = lotId;
        }

        public Long getRoastId() {
            return roastId;
        }

        public Long getLotId() {
            return lotId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof RoastLotId other && Objects.equals(roastId, other.roastId)
                    && Objects.equals(lotId, other.lotId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roastId, lotId);
        }
    }

    /** Green coffee of one lot used in a roast (several rows make a blend). */
    @Entity(name = "RoastLot")
    @Table(name = "roast_lot")
    public static class RoastLotEntity {

        @EmbeddedId
        private RoastLotId id;

        @Column(name = "green_weight_g", nullable = false, precision = 10, scale = 1)
        private BigDecimal greenWeightG;

        protected RoastLotEntity() {
        }

        public RoastLotEntity(Long roastId, Long lotId, BigDecimal greenWeightG) {
            this.id = new RoastLotId(roastId, lotId);
            this.greenWeightG = greenWeightG;
        }

        public RoastLotId getId() {
            return id;
        }

        public BigDecimal getGreenWeightG() {
            return greenWeightG;
        }
    }

}
