package dev.roastbench.roast;

import dev.roastbench.roast.RoastParts.CurveData;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurveRepository extends JpaRepository<CurveData, Long> {
}
