package dev.roastbench.roast;

import dev.roastbench.analysis.EventType;
import dev.roastbench.analysis.PhaseMetrics;
import dev.roastbench.analysis.RoastMetrics;
import dev.roastbench.imports.SourceFormat;
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

/**
 * One roast. The curve, events and lots are in other tables. The main numbers (time, DTR, loss) are saved here too,
 * so the list can sort and filter fast.
 */
@Entity
@Table(name = "roast")
public class Roast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_no", nullable = false, updatable = false)
    private long batchNo;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "roasted_at", nullable = false)
    private Instant roastedAt;

    @Column(length = 120)
    private String machine;

    @Column(name = "profile_id")
    private Long profileId;

    @Column(name = "green_weight_g", precision = 10, scale = 1)
    private BigDecimal greenWeightG;

    @Column(name = "roasted_weight_g", precision = 10, scale = 1)
    private BigDecimal roastedWeightG;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_format", nullable = false, length = 20)
    private SourceFormat sourceFormat;

    @Column(name = "source_filename", length = 255)
    private String sourceFilename;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "total_time_s", nullable = false)
    private double totalTimeS;

    @Column(name = "drop_estimated", nullable = false)
    private boolean dropEstimated;

    @Column(name = "charge_bt", nullable = false)
    private double chargeBt;

    @Column(name = "drop_bt", nullable = false)
    private double dropBt;

    @Column(name = "tp_time_s", nullable = false)
    private double tpTimeS;

    @Column(name = "tp_bt", nullable = false)
    private double tpBt;

    @Column(name = "dry_end_s")
    private Double dryEndS;

    @Column(name = "first_crack_s")
    private Double firstCrackS;

    @Column(name = "dev_time_s")
    private Double devTimeS;

    @Column(name = "dtr_pct")
    private Double dtrPct;

    @Column(name = "loss_pct")
    private Double lossPct;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Roast() {
    }

    public Roast(long batchNo, String name, Instant roastedAt, SourceFormat sourceFormat) {
        this.batchNo = batchNo;
        this.name = name;
        this.roastedAt = roastedAt;
        this.sourceFormat = sourceFormat;
    }

    /** Copies the summary figures of a fresh analysis into the stored columns. */
    public void applyMetrics(RoastMetrics m) {
        totalTimeS = m.totalTimeSeconds();
        dropEstimated = m.dropEstimated();
        chargeBt = m.chargeBeanTemp();
        dropBt = m.dropBeanTemp();
        tpTimeS = m.turningPointSeconds();
        tpBt = m.turningPointBeanTemp();
        dryEndS = m.eventTime(EventType.DRY_END);
        firstCrackS = m.eventTime(EventType.FIRST_CRACK_START);
        PhaseMetrics development = m.phase(PhaseMetrics.Phase.DEVELOPMENT);
        devTimeS = development == null ? null : development.durationSeconds();
        dtrPct = m.developmentTimeRatioPercent();
        lossPct = m.roastLossPercent();
    }

    public String batchCode() {
        return String.format("PR-%04d", batchNo);
    }

    public Long getId() {
        return id;
    }

    public long getBatchNo() {
        return batchNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getRoastedAt() {
        return roastedAt;
    }

    public String getMachine() {
        return machine;
    }

    public void setMachine(String machine) {
        this.machine = machine;
    }

    public Long getProfileId() {
        return profileId;
    }

    public void setProfileId(Long profileId) {
        this.profileId = profileId;
    }

    public BigDecimal getGreenWeightG() {
        return greenWeightG;
    }

    public void setGreenWeightG(BigDecimal greenWeightG) {
        this.greenWeightG = greenWeightG;
    }

    public BigDecimal getRoastedWeightG() {
        return roastedWeightG;
    }

    public void setRoastedWeightG(BigDecimal roastedWeightG) {
        this.roastedWeightG = roastedWeightG;
    }

    public SourceFormat getSourceFormat() {
        return sourceFormat;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public void setSourceFilename(String sourceFilename) {
        this.sourceFilename = sourceFilename;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public double getTotalTimeS() {
        return totalTimeS;
    }

    public double getChargeBt() {
        return chargeBt;
    }

    public double getDropBt() {
        return dropBt;
    }

    public double getTpTimeS() {
        return tpTimeS;
    }

    public double getTpBt() {
        return tpBt;
    }

    public Double getDryEndS() {
        return dryEndS;
    }

    public Double getFirstCrackS() {
        return firstCrackS;
    }

    public Double getDevTimeS() {
        return devTimeS;
    }

    public Double getDtrPct() {
        return dtrPct;
    }

    public Double getLossPct() {
        return lossPct;
    }

    public boolean isDropEstimated() {
        return dropEstimated;
    }
}
