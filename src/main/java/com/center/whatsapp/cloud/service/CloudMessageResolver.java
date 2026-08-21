package com.center.whatsapp.cloud.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.common.tenant.TenantContext;
import com.center.whatsapp.cloud.entity.WhatsappCloudTemplate;
import com.center.whatsapp.cloud.entity.WhatsappCloudTemplateVar;
import com.center.whatsapp.cloud.entity.WhatsappTypeTemplate;
import com.center.whatsapp.cloud.repository.WhatsappCloudTemplateRepository;
import com.center.whatsapp.service.WhatsappAvailabilityService;
import com.center.whatsapp.service.WhatsappResponsibilityCatalog;

import lombok.RequiredArgsConstructor;

/**
 * Turns "we are about to send an ATTENDANCE message, and here are the values that
 * message was built from" into the approved Meta template that carries it.
 *
 * <p>WhatsApp only lets a business start a conversation with a template it has
 * already reviewed, so the Arabic wording a teacher types is never what leaves -
 * it is what the history records. The template's placeholders are numbered, not
 * named, so the mapping saved on the template - position 1 is
 * {@code student.name}, position 2 is {@code lesson.name} - is what makes the
 * same variables that rendered that wording fill the template in the right
 * order.
 *
 * <p>Nothing here guesses. A type with no mapped template resolves to empty and
 * the send is recorded as failed with a reason a person can act on, rather than
 * being fired at Meta to come back as a rejection nobody can read.
 */
@Service
@RequiredArgsConstructor
public class CloudMessageResolver {

    private final WhatsappAvailabilityService availability;
    private final WhatsappCloudTemplateRepository templates;

    /**
     * A template ready to send.
     *
     * @param params         the body values, in placeholder order
     * @param headerParam    the value for a TEXT header that carries a
     *                       placeholder, or null when the header takes none
     * @param urlButtonParam the value for a dynamic URL button, or null
     * @param category       Meta's billing category, recorded on the log row
     * @param wantsDocument  whether the template's header expects a file
     */
    public record Resolved(String name, String language, List<String> params, String headerParam,
            String urlButtonParam, String category, boolean wantsDocument, String text) {
    }

    /**
     * A template's wording, ready to be filled for any number of students.
     *
     * <p>This exists because the history was lying. The log's {@code body} column
     * used to hold a SEPARATE hand-written copy of each message, kept in
     * {@code wa_message_variant} and seeded from a hardcoded default - so a
     * teacher read one wording in the log while the parent's phone showed
     * another, and the two drifted the moment a template was edited. The only
     * text that is true is the template's own, filled from the same values the
     * send fills its placeholders from.
     *
     * <p>Plain data plus a pure method, deliberately: a lesson of a hundred
     * students reads the template ONCE and fills it a hundred times in memory.
     * Resolving per student would put two queries per recipient inside the
     * planning transaction.
     */
    public record Wording(String templateName, String headerFormat, String headerText,
            String headerVar, String bodyText, List<String> varKeys) {

        /**
         * The message as it lands: a TEXT header on its own line, then the body,
         * with every placeholder replaced.
         *
         * <p>A blank value becomes a dash, matching what {@code CloudApiClient}
         * actually sends for it - so the log shows the visible gap the parent
         * saw, rather than a tidier sentence than the one that arrived.
         */
        public String fill(Map<String, String> vars) {
            String body = bodyText == null ? "" : bodyText;
            for (int i = 0; i < varKeys.size(); i++) {
                String key = varKeys.get(i);
                String value = key == null || vars == null ? null : vars.get(key);
                body = body.replace("{{" + (i + 1) + "}}",
                        value == null || value.isBlank() ? "-" : value);
            }
            if (!"TEXT".equalsIgnoreCase(headerFormat) || headerText == null
                    || headerText.isBlank()) {
                return body;
            }
            // A TEXT header takes at most one value, always {{1}} - Meta allows
            // no more - so the template's own header variable is the whole
            // mapping.
            String head = headerText;
            String value = headerVar == null || vars == null ? null : vars.get(headerVar);
            head = head.replace("{{1}}", value == null || value.isBlank() ? "-" : value);
            return body.isBlank() ? head : head + "\n\n" + body;
        }
    }

    /**
     * The wording bound to one message type, read once.
     *
     * <p>Empty when nothing approved carries the type, which is the same
     * condition {@link #forCode} refuses on - so a caller that cannot get a
     * wording also cannot send, and the two answers cannot disagree.
     */
    @Transactional(readOnly = true)
    public Optional<Wording> wordingFor(String code) {
        return template(code).map(CloudMessageResolver::wordingOf);
    }

    /** Package-visible so the preview screen fills the same object the send does. */
    public static Wording wordingOf(WhatsappCloudTemplate template) {
        return new Wording(template.getName(), template.getHeaderFormat(),
                template.getHeaderText(), template.getHeaderVar(), template.getBodyText(),
                varKeys(template));
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

    /** The approved template bound to a message type for the current tenant. */
    private Optional<WhatsappCloudTemplate> template(String code) {
        WhatsappTypeTemplate mapping = availability.templateMapping(TenantContext.get()).get(code);
        if (mapping == null) {
            return Optional.empty();
        }
        return templates.findById(mapping.getTemplateId())
                .filter(t -> "APPROVED".equalsIgnoreCase(t.getStatus()));
    }

    /**
     * The template for one message origin, filled from {@code vars}.
     *
     * @param origin       the message log origin, e.g. {@code ATTENDANCE}
     * @param vars         the same variable map the message body was rendered from
     * @param sendingPhone the number the message leaves from, used for a wa.me
     *                     button when the mapping does not name another line
     */
    @Transactional(readOnly = true)
    public Optional<Resolved> forOrigin(String origin, Map<String, String> vars,
            String sendingPhone) {
        return forCode(WhatsappResponsibilityCatalog.forOrigin(origin), vars, sendingPhone);
    }

    /** As above, naming the message type directly. */
    @Transactional(readOnly = true)
    public Optional<Resolved> forCode(String code, Map<String, String> vars, String sendingPhone) {
        UUID owner = TenantContext.get();
        WhatsappTypeTemplate mapping = availability.templateMapping(owner).get(code);
        if (mapping == null) {
            return Optional.empty();
        }
        WhatsappCloudTemplate template = templates.findById(mapping.getTemplateId()).orElse(null);
        if (template == null || !"APPROVED".equalsIgnoreCase(template.getStatus())) {
            return Optional.empty();
        }

        return Optional.of(new Resolved(
                template.getName(),
                template.getLanguage(),
                params(template, vars),
                headerValue(template, vars),
                buttonValue(template, mapping, sendingPhone),
                template.getCategory(),
                "DOCUMENT".equalsIgnoreCase(template.getHeaderFormat()),
                // The same words the recipient will read, for the history row.
                wordingOf(template).fill(vars)));
    }

    /**
     * What fills a TEXT header, or null when the header takes nothing.
     *
     * <p>The template's own wording decides, not the saved mapping: a header
     * without <code>{{1}}</code> is static and Meta rejects a value for it, while
     * a header WITH one must be given something or the send fails before the body
     * is ever looked at. So an unmapped or empty variable still sends a dash -
     * the same visible-gap rule the body follows - rather than nothing at all.
     */
    private static String headerValue(WhatsappCloudTemplate template, Map<String, String> vars) {
        if (!"TEXT".equalsIgnoreCase(template.getHeaderFormat())
                || CloudApiClient.countPlaceholders(template.getHeaderText()) == 0) {
            return null;
        }
        String key = template.getHeaderVar();
        String value = key == null || vars == null ? null : vars.get(key);
        return value == null || value.isBlank() ? "-" : value;
    }

    /**
     * The body values, one per placeholder, in order.
     *
     * <p>Sized by the template's own parameter count rather than by how many
     * mappings were saved: Meta rejects a send whose parameter count does not
     * match the template exactly, so a half-finished mapping must still produce a
     * full-length list. The unmapped slots come out blank, and
     * {@code CloudApiClient} turns blank into a dash so the message is visibly
     * incomplete instead of silently truncated.
     */
    private static List<String> params(WhatsappCloudTemplate template, Map<String, String> vars) {
        Map<Integer, String> byPosition = new java.util.HashMap<>();
        for (WhatsappCloudTemplateVar v : template.getVars()) {
            byPosition.put(v.getPosition(), v.getVarKey());
        }
        List<String> out = new ArrayList<>(template.getBodyParams());
        for (int i = 1; i <= template.getBodyParams(); i++) {
            String key = byPosition.get(i);
            String value = key == null || vars == null ? null : vars.get(key);
            out.add(value == null ? "" : value);
        }
        return out;
    }

    /**
     * What a wa.me button points at: the line the mapping names, or the number
     * the message is leaving from. Null when the template has no such button -
     * sending a value for a static button is rejected by Meta.
     */
    private static String buttonValue(WhatsappCloudTemplate template, WhatsappTypeTemplate mapping,
            String sendingPhone) {
        if (!template.isHasUrlButton()) {
            return null;
        }
        String explicit = mapping.getUrlButtonValue();
        return explicit != null && !explicit.isBlank() ? explicit : sendingPhone;
    }
}
