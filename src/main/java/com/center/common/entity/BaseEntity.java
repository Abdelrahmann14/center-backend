package com.center.common.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
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
public abstract class BaseEntity implements Persistable<UUID> {

    // A plainly ASSIGNED id, defaulted to a fresh UUID - deliberately NOT a
    // Hibernate generator (@GeneratedValue or the old @AssignedOrUuid). That
    // distinction is the whole fix for the offline write path.
    //
    // The offline client mints the row's UUID itself so the row it showed the
    // user and the row the server stores are the same row. But with a GENERATOR,
    // Hibernate treats a populated id as proof the row was already persisted -
    // "a generated id is null until insert" - so persist() on the replayed row
    // threw "detached entity passed to persist", and merge() on it threw
    // StaleObjectStateException (it tried to UPDATE a row that does not exist).
    // No @Version type could paper over that: the id itself was the tell.
    //
    // An assigned id carries no such assumption - a set id is its normal state
    // before an insert - so persist() inserts it. The default UUID keeps every
    // existing caller working unchanged: they build the entity without an id and
    // still get one (the same random v4 the old generator produced), while the
    // sync writer overwrites it with the client's id via setId() before saving.
    @Id
    private UUID id = UUID.randomUUID();

    // True until this instance has been loaded from, or written to, the database.
    //
    // This is the linchpin of the offline write path, and the reason the whole
    // class implements Persistable. With an assigned id, Spring Data's save()
    // cannot tell a new row from an edit (the id is always set), so it would
    // merge everything - a SELECT-then-UPDATE that finds nothing for a brand-new
    // offline row. Persistable.isNew() makes the call explicit: a just-built
    // instance is new (persist -> INSERT), a loaded one is not (merge -> UPDATE).
    @Transient
    private boolean persisted;

    @Override
    public boolean isNew() {
        return !persisted;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        this.persisted = true;
    }

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

    // Primitive: a new instance is version 0, which Hibernate seeds and inserts.
    // (New-vs-existing is decided by isNew() above, not by this - a boxed version
    // whose null read as "detached" is exactly the trap that broke the offline
    // insert before.)
    @Version
    @Column(nullable = false)
    private long version;

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
