package com.center.common.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Identity, auditing and optimistic locking for every editable entity.
 *
 * <p>Append-only tables (attendance, work_sessions) and child rows
 * (center_grades) deliberately do not extend this - they are never edited, so
 * auditing and a version column would be dead weight.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity {

    // Assigned-or-generate rather than plain @GeneratedValue: an offline client
    // creates the row and its UUID together, and that id has to survive the sync
    // or the row comes back down the feed as a second, indistinguishable copy.
    // Callers that do not set an id (all of them, today) are unaffected.
    @Id
    @AssignedOrUuid
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;

    // Boxed, NOT primitive - this is what makes an assigned-id insert work.
    //
    // Spring Data's save() has to decide insert vs update. With a PRIMITIVE
    // @Version it cannot use the version (a primitive is never null), so it falls
    // back to the id: id present => "not new" => merge => UPDATE. That is right
    // for a generated id (null until persist) but wrong for the offline path,
    // which assigns the client's id BEFORE saving a brand-new row - the UPDATE
    // then hits no row and Hibernate throws StaleObjectStateException, so every
    // offline-created student/lesson/group was refused on sync.
    //
    // A nullable Long is null on a new instance and set once loaded, so save()
    // reads new-ness from the version and inserts the new row correctly. The
    // column stays NOT NULL: Hibernate seeds the version to 0 on insert.
    @Version
    @Column(nullable = false)
    private Long version;

    /**
     * Identity is the primary key. Unsaved entities (null id) are only ever
     * equal to themselves, and proxies are unwrapped so a lazy reference still
     * compares equal to its loaded twin.
     */
    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        Class<?> thisType = effectiveClass(this);
        Class<?> otherType = effectiveClass(other);
        if (!thisType.equals(otherType)) {
            return false;
        }
        UUID thisId = getId();
        return thisId != null && thisId.equals(((BaseEntity) other).getId());
    }

    @Override
    public final int hashCode() {
        // Constant per type: a generated id changes on persist, and a mutable
        // hash would strand the entity in any hash-based collection.
        return effectiveClass(this).hashCode();
    }

    private static Class<?> effectiveClass(Object o) {
        return o instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : o.getClass();
    }
}
