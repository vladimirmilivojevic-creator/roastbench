package dev.roastbench.inventory;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LotRepository extends JpaRepository<Lot, Long> {

    boolean existsByCodeIgnoreCase(String code);

    Optional<Lot> findByCodeIgnoreCase(String code);

    /** Row lock: movements for one lot are serialised so stock can never go negative under concurrency. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lot l where l.id = :id")
    Optional<Lot> findByIdForUpdate(@Param("id") Long id);
}
