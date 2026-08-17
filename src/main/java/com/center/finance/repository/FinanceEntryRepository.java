package com.center.finance.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.finance.entity.FinanceEntry;

public interface FinanceEntryRepository extends JpaRepository<FinanceEntry, UUID> {

    /** Every line in the window, bucketed by session in the service. */
    List<FinanceEntry> findBySessionDateBetweenOrderByCreatedAtAsc(LocalDate from, LocalDate to);

    /** One session's lines, in the order they were written. */
    List<FinanceEntry> findByLectureIdAndSessionDateOrderByCreatedAtAsc(UUID lectureId, LocalDate sessionDate);
}
