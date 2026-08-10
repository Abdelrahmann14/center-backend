package com.center.whatsapp.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite key for {@link WhatsappResponsibility}: one owner scope + one code. */
public class WhatsappResponsibilityId implements Serializable {

    private UUID ownerAdminId;
    private String code;

    public WhatsappResponsibilityId() {
    }

    public WhatsappResponsibilityId(UUID ownerAdminId, String code) {
        this.ownerAdminId = ownerAdminId;
        this.code = code;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WhatsappResponsibilityId that)) return false;
        return Objects.equals(ownerAdminId, that.ownerAdminId) && Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerAdminId, code);
    }
}
