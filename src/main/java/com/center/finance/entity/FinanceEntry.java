package com.center.finance.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.center.common.entity.TenantEntity;
import com.center.common.enums.FinanceEntryKind;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One hand-written line on a lesson invoice: money that came in or went out
 * beyond what the registrations account for.
 *
 * <p>It points at a session, not at a lesson: the session key is
 * (lecture, group, date), because the same lesson taught to two groups produces
 * two invoices and a cost belongs to exactly one of them. The ids are plain
 * columns rather than associations - the invoice reader already loads every
 * lecture and group it needs in one go, so a managed reference here would only
 * add a lazy proxy per line.
 */
@Entity
@Table(name = "finance_entries", indexes = {
        @Index(name = "finance_entries_session_idx", columnList = "admin_id, session_date"),
        @Index(name = "finance_entries_lecture_idx", columnList = "admin_id, lecture_id, group_id")
})
@Getter
@Setter
@NoArgsConstructor
public class FinanceEntry extends TenantEntity {

    @Column(name = "lecture_id", nullable = false)
    private UUID lectureId;

    /** Null for a session registered under no group. */
    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(nullable = false)
    private FinanceEntryKind kind;

    @Column(nullable = false)
    private String description;

    /** Always positive; {@link #kind} decides the sign on the invoice. */
    @Column(nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;
}
