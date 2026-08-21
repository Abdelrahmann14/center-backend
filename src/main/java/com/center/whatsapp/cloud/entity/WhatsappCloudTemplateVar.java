package com.center.whatsapp.cloud.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Which system variable fills one <code>{{n}}</code> of a Meta template.
 *
 * <p>Meta's placeholders are numbered, not named: the template body says
 * <code>{{1}}</code> and nothing about it says whether that is a student's name
 * or a lesson time. This row is the missing half - it pins position 1 to
 * {@code student.name}, and the send fills the values in that order.
 *
 * <p>{@code varKey} is a key from {@code VariableCatalog}, the same list the
 * message composer offers, so a person maps "الاسم" rather than typing a token.
 */
@Entity
@Table(name = "wa_cloud_template_var")
@IdClass(WhatsappCloudTemplateVarId.class)
@Getter
@Setter
@NoArgsConstructor
public class WhatsappCloudTemplateVar {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private WhatsappCloudTemplate template;

    /** The placeholder number, 1-based, exactly as Meta counts them. */
    @Id
    @Column(name = "var_position", nullable = false)
    private int position;

    @Column(name = "var_key", nullable = false)
    private String varKey;

    public WhatsappCloudTemplateVar(WhatsappCloudTemplate template, int position, String varKey) {
        this.template = template;
        this.position = position;
        this.varKey = varKey;
    }
}
