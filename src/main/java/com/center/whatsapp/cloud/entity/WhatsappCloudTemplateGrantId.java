package com.center.whatsapp.cloud.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Composite key of {@link WhatsappCloudTemplateGrant}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WhatsappCloudTemplateGrantId implements Serializable {

    private UUID templateId;
    private UUID adminId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WhatsappCloudTemplateGrantId other)) return false;
        return Objects.equals(templateId, other.templateId)
                && Objects.equals(adminId, other.adminId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(templateId, adminId);
    }
}
