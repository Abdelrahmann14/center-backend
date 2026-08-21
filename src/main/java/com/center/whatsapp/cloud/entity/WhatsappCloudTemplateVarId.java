package com.center.whatsapp.cloud.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Composite key of {@link WhatsappCloudTemplateVar}: the template and which
 * placeholder within it. The {@code template} field is the UUID of the related
 * template - the derived-identity form JPA expects alongside an {@code @Id}
 * association.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WhatsappCloudTemplateVarId implements Serializable {

    private UUID template;
    private int position;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WhatsappCloudTemplateVarId other)) return false;
        return position == other.position && Objects.equals(template, other.template);
    }

    @Override
    public int hashCode() {
        return Objects.hash(template, position);
    }
}
