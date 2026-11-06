package dev.roastbench.roast;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface RoastRepository extends JpaRepository<Roast, Long>, JpaSpecificationExecutor<Roast> {

    interface ProfileStats {
        Long getProfileId();

        long getRoastCount();

        Double getAvgDtr();

        Double getAvgLoss();

        Double getAvgTotalTime();
    }

    interface WindowStats {
        long getRoastCount();

        Double getAvgDtr();

        Double getAvgLoss();

        java.math.BigDecimal getGreenG();
    }

    @Query(value = "select nextval('roast_batch_seq')", nativeQuery = true)
    long nextBatchNo();

    @Query("select count(r) as roastCount, avg(r.dtrPct) as avgDtr, avg(r.lossPct) as avgLoss, "
            + "sum(r.greenWeightG) as greenG from Roast r where r.roastedAt >= :since")
    WindowStats windowStats(@org.springframework.data.repository.query.Param("since") java.time.Instant since);

    @Query("select r.profileId as profileId, count(r) as roastCount, avg(r.dtrPct) as avgDtr, "
            + "avg(r.lossPct) as avgLoss, avg(r.totalTimeS) as avgTotalTime "
            + "from Roast r where r.profileId is not null group by r.profileId")
    List<ProfileStats> profileStats();
}
