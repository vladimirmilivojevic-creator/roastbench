package dev.roastbench.roast;

import dev.roastbench.roast.RoastParts.RoastLotEntity;
import dev.roastbench.roast.RoastParts.RoastLotId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoastLotRepository extends JpaRepository<RoastLotEntity, RoastLotId> {

    @Query("select rl from RoastLot rl where rl.id.roastId in :roastIds")
    List<RoastLotEntity> findByRoastIds(@Param("roastIds") Collection<Long> roastIds);
}
