package com.center.center.repository;
import com.center.center.entity.Center;
import com.center.group.entity.Group;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.center.center.entity.CenterGrade;

public interface CenterGradeRepository extends JpaRepository<CenterGrade, CenterGrade.Key> {

    List<CenterGrade> findByIdCenterIdOrderByIdGradeAsc(UUID centerId);

    void deleteByIdCenterId(UUID centerId);

    /**
     * The center's configured price for a group's grade, matched through the
     * group's denormalised center name.
     */
    @Query("""
            SELECT cg.price FROM CenterGrade cg, Center c, Group g
            WHERE cg.id.centerId = c.id
              AND c.name = g.centerName
              AND cg.id.grade = g.grade
              AND g.id = :groupId
            """)
    Optional<BigDecimal> findPriceForGroup(@Param("groupId") UUID groupId);
}
