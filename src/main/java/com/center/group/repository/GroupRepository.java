package com.center.group.repository;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.center.group.entity.Group;

public interface GroupRepository extends JpaRepository<Group, UUID> {

    List<Group> findAllByOrderByDayOfWeekAscStartTimeAsc();

    /** Active groups for one grade (tenant-scoped) - the registration group dropdown. */
    List<Group> findByGradeAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(String grade);

    /** Guards the (day_of_week, start_time) unique constraint before writing. */
    boolean existsByDayOfWeekAndStartTime(short dayOfWeek, LocalTime startTime);

    boolean existsByDayOfWeekAndStartTimeAndIdNot(short dayOfWeek, LocalTime startTime, UUID id);

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
