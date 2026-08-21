package com.center.whatsapp.cloud.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Composite key of {@link WhatsappTypeTemplate}: (owner scope, message type). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WhatsappTypeTemplateId implements Serializable {

    private UUID ownerAdminId;
    private String code;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WhatsappTypeTemplateId other)) return false;
        return Objects.equals(ownerAdminId, other.ownerAdminId)
                && Objects.equals(code, other.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerAdminId, code);
    }
}
