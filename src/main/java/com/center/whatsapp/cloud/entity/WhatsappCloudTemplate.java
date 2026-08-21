package com.center.whatsapp.cloud.entity;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A local mirror of one message template that lives in Meta's WhatsApp Manager.
 *
 * <p>Meta is the source of truth: templates are authored and reviewed there, and
 * rows here are refreshed from it - never invented. The mirror exists so a page
 * can list what the system is allowed to send without a Graph call per load, and
 * so an automation can be mapped to a template that is known to be approved.
 *
 * <p>Deliberately NOT tenant-scoped: templates belong to the WhatsApp Business
 * Account, which is the platform's, not a teacher's.
 */
@Entity
@Table(name = "wa_cloud_template")
@Getter
@Setter
@NoArgsConstructor
public class WhatsappCloudTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Meta's own id for the template - the key rows are matched on when syncing. */
    @Column(name = "meta_template_id", nullable = false, updatable = false)
    private String metaTemplateId;

    @Column(nullable = false)
    private String name;

    /** e.g. {@code ar_EG}. A template is per language: same name, different rows. */
    @Column(nullable = false)
    private String language;

    /** UTILITY | MARKETING | AUTHENTICATION - Meta may change this on review. */
    @Column(nullable = false)
    private String category;

    /** APPROVED | PENDING | REJECTED | PAUSED | DISABLED. Only APPROVED can send. */
    @Column(nullable = false)
    private String status;

    @Column(name = "body_text", columnDefinition = "text")
    private String bodyText;

    /** NONE | TEXT | IMAGE | DOCUMENT | VIDEO. */
    @Column(name = "header_format", nullable = false)
    private String headerFormat = "NONE";

    /**
     * The wording of a TEXT header, null for every other format. Kept because the
     * format alone does not say whether the header takes a value: only a header
     * containing <code>{{1}}</code> does, and a send that gets that wrong is
     * rejected by Meta whatever the body looks like.
     */
    @Column(name = "header_text", columnDefinition = "text")
    private String headerText;

    /** How many parameters the body takes; a send with the wrong count is rejected. */
    @Column(name = "body_params", nullable = false)
    private int bodyParams;

    /** Whether Meta reports a button whose URL ends in a placeholder. Read, never set. */
    @Column(name = "has_url_button", nullable = false)
    private boolean hasUrlButton;

    /** A plain-Arabic name for the screens, since Meta's own name is a slug. */
    @Column(name = "label")
    private String label;

    /** The system variable that fills a TEXT header, when the template has one. */
    @Column(name = "header_var")
    private String headerVar;

    /**
     * True (the default) means every account may use this template. False means
     * only the accounts in {@code wa_cloud_template_grant} may - which is how one
     * teacher gets a template written for them without it appearing everywhere.
     */
    @Column(name = "shared_all", nullable = false)
    private boolean sharedAll = true;

    /**
     * Which system variable fills each <code>{{n}}</code>, ordered by n.
     *
     * <p>Eagerly loaded and cascaded: a template is never useful without its
     * mapping - a send that guessed the order would put the lesson name where the
     * student's name belongs, and Meta would accept it.
     */
    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("position ASC")
    private List<WhatsappCloudTemplateVar> vars = new ArrayList<>();

    @Column(name = "rejected_reason", columnDefinition = "text")
    private String rejectedReason;

    @Column(name = "synced_at", nullable = false)
    private OffsetDateTime syncedAt = OffsetDateTime.now();

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
