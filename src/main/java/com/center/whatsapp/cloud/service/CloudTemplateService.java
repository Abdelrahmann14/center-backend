package com.center.whatsapp.cloud.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.common.exception.BusinessRuleException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.user.entity.User;
import com.center.user.repository.UserRepository;
import com.center.messaging.service.MessageText;
import com.center.notification.service.VariableCatalog;
import com.center.settings.service.SettingsService;
import com.center.whatsapp.cloud.dto.CloudTemplateMappingRequest;
import com.center.whatsapp.cloud.dto.CloudTemplateResponse;
import com.center.whatsapp.cloud.dto.CloudTypeTemplateRequest;
import com.center.whatsapp.cloud.dto.CloudTypeTemplateResponse;
import com.center.whatsapp.cloud.dto.WhatsappMessagePreview;
import com.center.whatsapp.cloud.entity.WhatsappCloudTemplate;
import com.center.whatsapp.cloud.entity.WhatsappCloudTemplateGrant;
import com.center.whatsapp.cloud.entity.WhatsappCloudTemplateVar;
import com.center.whatsapp.cloud.entity.WhatsappTypeTemplate;
import com.center.whatsapp.cloud.entity.WhatsappTypeTemplateId;
import com.center.whatsapp.cloud.repository.WhatsappCloudTemplateGrantRepository;
import com.center.whatsapp.cloud.repository.WhatsappCloudTemplateRepository;
import com.center.whatsapp.cloud.repository.WhatsappTypeTemplateRepository;
import com.center.whatsapp.cloud.service.CloudApiClient.TemplateInfo;
import com.center.whatsapp.service.WhatsappAvailabilityService;
import com.center.whatsapp.service.WhatsappResponsibilityCatalog;
import com.center.whatsapp.service.WhatsappResponsibilityCatalog.Responsibility;

import lombok.RequiredArgsConstructor;

/**
 * Keeps the local mirror of Meta's message templates in step with Meta, and holds
 * the three decisions Meta knows nothing about: what to call a template, which
 * system variable fills each of its numbered placeholders, and who may use it.
 *
 * <p>Templates are authored and reviewed in WhatsApp Manager - this system does
 * not create them. Two reasons: Meta's review is the gate that decides whether a
 * template exists at all, and a template's text is a compliance artefact that a
 * teacher should not be able to edit from an admin screen and have go out under
 * the platform's account.
 *
 * <p>What the mirror buys is that every screen which needs "what may I send?" can
 * answer from the database, and a message type can be pointed at a template that
 * is known to be approved rather than a name someone typed.
 */
@Service
@RequiredArgsConstructor
public class CloudTemplateService {

    private final CloudApiClient cloud;
    private final WhatsappCloudTemplateRepository repo;
    private final WhatsappCloudTemplateGrantRepository grants;
    private final WhatsappTypeTemplateRepository typeTemplates;
    private final WhatsappAvailabilityService availability;
    private final UserRepository users;
    private final SettingsService settings;

    /** What the mirror currently holds. Never calls out. */
    @Transactional(readOnly = true)
    public List<CloudTemplateResponse> list() {
        return repo.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    /** Only the ones a message type may be mapped to. */
    @Transactional(readOnly = true)
    public List<CloudTemplateResponse> approved() {
        return repo.findByStatusOrderByNameAsc("APPROVED").stream().map(this::toResponse).toList();
    }

    /**
     * The approved templates one account is allowed to use: the ones shared with
     * everyone, plus the ones granted to it specifically.
     *
     * <p>This is what a teacher's screens show. A teacher never sees the whole
     * account's template list - a template written for one centre says that
     * centre's name in it, and offering it to another would be a leak with a
     * WhatsApp message attached.
     */
    @Transactional(readOnly = true)
    public List<CloudTemplateResponse> availableFor(UUID adminId) {
        Set<UUID> granted = new HashSet<>();
        if (adminId != null) {
            grants.findByAdminId(adminId).forEach(g -> granted.add(g.getTemplateId()));
        }
        return repo.findByStatusOrderByNameAsc("APPROVED").stream()
                .filter(t -> t.isSharedAll() || granted.contains(t.getId()))
                .map(this::toResponse)
                .toList();
    }

    /**
     * Every message type as the parent will read it.
     *
     * <p>A teacher does not configure any of this - the templates are written and
     * approved by the platform - so the only useful thing this screen can do is
     * show them the wording that leaves in their name. The placeholders are
     * filled from the variable catalogue's own examples, which is the same source
     * the composer uses for its chips, so a preview never invents a value the
     * real message could not produce.
     */
    @Transactional(readOnly = true)
    public List<WhatsappMessagePreview> previewsFor(UUID owner) {
        Map<String, String> examples = new java.util.HashMap<>();
        for (VariableCatalog.Variable v : VariableCatalog.ALL) {
            examples.put(v.key(), v.example());
        }
        // Anything about the account itself is not a sample - the teacher's own
        // name and today's date are known, and a preview that says "أ. خالد" to
        // someone called أ. محمد is not a preview of their message. Only the
        // per-student values stay as examples, because a preview has no student.
        examples.putAll(MessageText.globals(teacherOf(owner)));
        // Built on messageTypes rather than beside it: whether a type can send is
        // a decision with several parts (the account enabled, a number up, an
        // approved template), and a second copy of that reasoning would drift.
        return availability.messageTypes(owner).stream()
                .map(t -> {
                    WhatsappCloudTemplate template = t.templateId() == null ? null
                            : repo.findById(t.templateId()).orElse(null);
                    return new WhatsappMessagePreview(
                            t.code(),
                            t.label(),
                            t.description(),
                            t.carriesFile(),
                            t.ready(),
                            t.blockedReason(),
                            template == null ? null : displayName(template),
                            template == null ? null : fillExamples(template, examples));
                })
                .toList();
    }

    /**
     * The workspace owner - the name the message signs with and the number it
     * asks the reader to call back on.
     *
     * <p>Same rule the real send uses: the account's own name, falling back to
     * the platform sender only when it has none, so the preview signs itself
     * exactly the way a sent message would.
     */
    private MessageText.Teacher teacherOf(UUID owner) {
        return (owner == null ? java.util.Optional.<User>empty() : users.findById(owner))
                .map(u -> new MessageText.Teacher(
                        u.getUsername() == null || u.getUsername().isBlank()
                                ? settings.senderName() : u.getUsername(),
                        u.getOfficePhone()))
                .orElseGet(() -> new MessageText.Teacher(settings.senderName(), null));
    }

    /**
     * The template as it lands - a TEXT header on its own line, then the body,
     * with every {@code {{1}}} swapped for what position 1 actually renders to.
     *
     * <p>The header is included because the parent reads it as the first line of
     * the message; leaving it out would show a preview shorter than what arrives.
     *
     * <p>An unmapped position keeps its number rather than vanishing: a preview
     * with a hole in it is a mapping someone still has to finish, and hiding that
     * would make a broken template look ready.
     */
    private static String fillExamples(WhatsappCloudTemplate template, Map<String, String> examples) {
        List<String> keys = varKeys(template);
        String body = template.getBodyText() == null ? "" : template.getBodyText();
        for (int i = 0; i < keys.size(); i++) {
            String value = keys.get(i) == null ? null : examples.get(keys.get(i));
            if (value != null) {
                body = body.replace("{{" + (i + 1) + "}}", value);
            }
        }

        if (!"TEXT".equalsIgnoreCase(template.getHeaderFormat())
                || template.getHeaderText() == null || template.getHeaderText().isBlank()) {
            return body;
        }
        // A TEXT header takes at most one value, always {{1}} - Meta allows no
        // more - so the template's own header variable is the whole mapping.
        String head = template.getHeaderText();
        String value = template.getHeaderVar() == null ? null
                : examples.get(template.getHeaderVar());
        if (value != null) {
            head = head.replace("{{1}}", value);
        }
        return body.isBlank() ? head : head + "\n\n" + body;
    }

    /** The variable filling each placeholder, position 1 first, gaps preserved. */
    private static List<String> varKeys(WhatsappCloudTemplate row) {
        List<String> keys = new ArrayList<>();
        for (WhatsappCloudTemplateVar v : row.getVars()) {
            while (keys.size() < v.getPosition() - 1) {
                keys.add(null);
            }
            keys.add(v.getVarKey());
        }
        return keys;
    }

    /**
     * Adopts one template by the id shown on its page in WhatsApp Manager,
     * reading the real thing back from Meta.
     *
     * <p>Re-importing an id already held is not an error: it refreshes that row,
     * which is exactly what someone does after fixing a rejected template. The
     * mapping already saved against it survives, since the row keeps its identity.
     */
    @Transactional
    public CloudTemplateResponse importById(String metaTemplateId, String label) {
        TemplateInfo info = cloud.fetchTemplate(metaTemplateId.trim());
        WhatsappCloudTemplate row = repo.findByMetaTemplateId(info.id())
                .orElseGet(WhatsappCloudTemplate::new);
        apply(row, info);
        if (label != null && !label.isBlank()) {
            row.setLabel(label.trim());
        }
        return toResponse(repo.save(row));
    }

    /**
     * Re-reads every template from Meta and writes it into the mirror.
     *
     * <p>Rows are matched on Meta's template id and updated in place, so a
     * template that has just been approved keeps its identity - anything already
     * pointing at it stays pointed at it, mapping included. A template deleted in
     * WhatsApp Manager is dropped here too: leaving it would let a message type
     * be mapped to something that no longer exists, and the failure would only
     * surface at the moment a parent was supposed to get a message.
     */
    @Transactional
    public List<CloudTemplateResponse> sync() {
        List<TemplateInfo> live = cloud.listTemplates();

        for (TemplateInfo info : live) {
            WhatsappCloudTemplate row = repo.findByMetaTemplateId(info.id())
                    .orElseGet(WhatsappCloudTemplate::new);
            apply(row, info);
            repo.save(row);
        }

        List<String> liveIds = live.stream().map(TemplateInfo::id).toList();
        List<WhatsappCloudTemplate> gone = repo.findAllByOrderByNameAsc().stream()
                .filter(row -> !liveIds.contains(row.getMetaTemplateId()))
                .toList();
        for (WhatsappCloudTemplate row : gone) {
            // The mappings have to go first: a message type left pointing at a
            // deleted template would be a foreign key violation on delete, and
            // worse, a silent promise the sender could not keep.
            typeTemplates.deleteByTemplateId(row.getId());
            grants.deleteByTemplateId(row.getId());
        }
        repo.deleteAll(gone);

        return list();
    }

    /** Everything Meta owns about a template, copied onto the row. */
    private static void apply(WhatsappCloudTemplate row, TemplateInfo info) {
        row.setMetaTemplateId(info.id());
        row.setName(info.name());
        row.setLanguage(info.language());
        row.setCategory(info.category());
        row.setStatus(info.status());
        row.setBodyText(info.bodyText());
        row.setHeaderFormat(info.headerFormat());
        row.setHeaderText(info.headerText());
        row.setBodyParams(info.bodyParams());
        row.setHasUrlButton(info.hasUrlButton());
        row.setRejectedReason(info.rejectedReason());
        row.setSyncedAt(OffsetDateTime.now());
    }

    /**
     * Saves what a person decided about a template: its readable name, the
     * variable behind each placeholder, and who may use it.
     *
     * <p>Every key is checked against the variable catalog. An unknown key would
     * be stored happily and then render as nothing at send time, and the person
     * reading the message would blame the template rather than the mapping.
     */
    @Transactional
    public CloudTemplateResponse saveMapping(UUID id, CloudTemplateMappingRequest req) {
        WhatsappCloudTemplate row = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("القالب غير موجود"));

        row.setLabel(blankToNull(req.label()));
        row.setHeaderVar(checkedKey(blankToNull(req.headerVar())));
        row.setSharedAll(req.sharedAll());

        row.getVars().clear();
        List<String> keys = req.varKeys() == null ? List.of() : req.varKeys();
        for (int i = 0; i < keys.size() && i < row.getBodyParams(); i++) {
            String key = checkedKey(blankToNull(keys.get(i)));
            if (key != null) {
                row.getVars().add(new WhatsappCloudTemplateVar(row, i + 1, key));
            }
        }
        repo.save(row);

        grants.deleteByTemplateId(id);
        if (!req.sharedAll() && req.adminIds() != null) {
            for (UUID adminId : new HashSet<>(req.adminIds())) {
                grants.save(new WhatsappCloudTemplateGrant(id, adminId));
            }
        }
        return toResponse(row);
    }

    /**
     * Records a status change Meta pushed over the webhook, so an approval shows
     * up without waiting for someone to press sync. Unknown templates are ignored:
     * the next full sync will pick them up with all their other fields.
     */
    @Transactional
    public void applyStatusUpdate(String metaTemplateId, String status, String reason) {
        repo.findByMetaTemplateId(metaTemplateId).ifPresent(row -> {
            row.setStatus(status);
            row.setRejectedReason(reason);
            row.setSyncedAt(OffsetDateTime.now());
            repo.save(row);
        });
    }

    // ---- which template carries which message type --------------------------

    /** The mapping in force for one owner scope, saying what it inherited. */
    @Transactional(readOnly = true)
    public List<CloudTypeTemplateResponse> typeTemplates(UUID owner, Map<String,
            WhatsappTypeTemplate> effective) {
        Set<String> own = new HashSet<>();
        typeTemplates.findByOwnerAdminId(WhatsappAvailabilityService.scopeOf(owner))
                .forEach(row -> own.add(row.getCode()));

        List<CloudTypeTemplateResponse> out = new ArrayList<>();
        for (Responsibility r : WhatsappResponsibilityCatalog.ALL) {
            WhatsappTypeTemplate row = effective.get(r.code());
            WhatsappCloudTemplate template = row == null ? null
                    : repo.findById(row.getTemplateId()).orElse(null);
            out.add(new CloudTypeTemplateResponse(
                    r.code(),
                    r.label(),
                    template == null ? null : template.getId(),
                    template == null ? null : displayName(template),
                    template == null ? null : template.getStatus(),
                    row == null ? null : row.getUrlButtonValue(),
                    row != null && !own.contains(r.code())));
        }
        return out;
    }

    /**
     * Points one message type at one template, or clears it.
     *
     * <p>Refuses a template that is not APPROVED and refuses one whose header
     * cannot carry the file this type sends. Both would be accepted by the screen
     * and rejected by Meta hours later, in front of a parent rather than in front
     * of the person who made the choice.
     */
    @Transactional
    public void assignType(UUID owner, String code, CloudTypeTemplateRequest req) {
        Responsibility type = WhatsappResponsibilityCatalog.find(code);
        if (type == null) {
            throw new BusinessRuleException("نوع رسالة غير معروف");
        }
        WhatsappTypeTemplateId key =
                new WhatsappTypeTemplateId(WhatsappAvailabilityService.scopeOf(owner), code);
        if (req.templateId() == null) {
            typeTemplates.findById(key).ifPresent(typeTemplates::delete);
            return;
        }

        WhatsappCloudTemplate template = repo.findById(req.templateId())
                .orElseThrow(() -> new ResourceNotFoundException("القالب غير موجود"));
        if (!"APPROVED".equalsIgnoreCase(template.getStatus())) {
            throw new BusinessRuleException("لا يمكن استخدام قالب غير معتمد من واتساب");
        }
        if (type.carriesFile() && !"DOCUMENT".equalsIgnoreCase(template.getHeaderFormat())) {
            throw new BusinessRuleException(
                    "هذه الرسالة ترسل ملفاً، فاختر قالباً رأسه من نوع ملف (DOCUMENT)");
        }
        typeTemplates.save(new WhatsappTypeTemplate(WhatsappAvailabilityService.scopeOf(owner),
                code, template.getId(), blankToNull(req.urlButtonValue())));
    }

    // ---- plumbing -----------------------------------------------------------

    private CloudTemplateResponse toResponse(WhatsappCloudTemplate row) {
        List<String> keys = varKeys(row);
        return new CloudTemplateResponse(
                row.getId(),
                row.getMetaTemplateId(),
                row.getName(),
                row.getLanguage(),
                row.getCategory(),
                row.getStatus(),
                row.getBodyText(),
                row.getHeaderFormat(),
                row.getHeaderText(),
                row.getBodyParams(),
                row.isHasUrlButton(),
                row.getLabel(),
                row.getHeaderVar(),
                keys,
                row.isSharedAll(),
                grants.findByTemplateId(row.getId()).stream()
                        .map(WhatsappCloudTemplateGrant::getAdminId).toList(),
                row.getRejectedReason());
    }

    private static String displayName(WhatsappCloudTemplate row) {
        return row.getLabel() != null && !row.getLabel().isBlank() ? row.getLabel() : row.getName();
    }

    /** A key the catalog actually advertises, or a refusal naming the bad one. */
    private static String checkedKey(String key) {
        if (key == null) {
            return null;
        }
        if (!VariableCatalog.keys().contains(key)) {
            throw new BusinessRuleException("متغيّر غير معروف: " + key);
        }
        return key;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
