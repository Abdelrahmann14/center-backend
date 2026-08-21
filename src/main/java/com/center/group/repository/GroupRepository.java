package com.center.group.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.center.group.entity.Group;

public interface GroupRepository extends JpaRepository<Group, UUID> {

    List<Group> findAllByOrderByDayOfWeekAscStartTimeAsc();

    interface DayCount {
        short getDayOfWeek();

        long getCount();
    }

    @Query("""
            SELECT g.dayOfWeek AS dayOfWeek, count(g) AS count
            FROM Group g GROUP BY g.dayOfWeek ORDER BY g.dayOfWeek
            """)
    List<DayCount> countByDayOfWeek();
}
