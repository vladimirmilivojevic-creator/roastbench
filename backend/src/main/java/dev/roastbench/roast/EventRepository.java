package dev.roastbench.roast;

import dev.roastbench.roast.RoastParts.EventEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<EventEntity, Long> {

    List<EventEntity> findByRoastId(Long roastId);

    List<EventEntity> findByRoastIdIn(Collection<Long> roastIds);

    @Modifying
    @Query("delete from RoastEvent e where e.roastId = :roastId")
    void deleteByRoastId(@Param("roastId") Long roastId);
}
