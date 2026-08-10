package com.center.common.entity;
import com.center.common.tenant.TenantContext;

import java.util.UUID;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * A workspace-scoped entity: it belongs to exactly one Admin and is invisible to
 * every other Admin.
 *
 * <p>{@link TenantId} makes Hibernate fill {@code admin_id} from the current
 * {@code TenantContext} on insert and append {@code admin_id = ?} to every
 * select, update and delete - so tenant isolation cannot be forgotten at a call
 * site. The field is read-only after insert: a row can never change owner.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class TenantEntity extends BaseEntity {

    @TenantId
    @Column(name = "admin_id", nullable = false, updatable = false)
    private UUID adminId;
}
