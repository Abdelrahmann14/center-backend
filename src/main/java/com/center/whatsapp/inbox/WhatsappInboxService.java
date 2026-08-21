package com.center.whatsapp.inbox;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.center.auth.security.AuthenticatedUser;
import com.center.common.exception.BusinessRuleException;
import com.center.common.tenant.TenantContext;
import com.center.whatsapp.cloud.service.CloudApiClient;
import com.center.whatsapp.cloud.service.WhatsappThrottle;
import com.center.whatsapp.service.WaPhone;
import com.center.whatsapp.service.WhatsappInstanceService;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The two-way half of WhatsApp: threads with real people, and replies written by
 * a human.
 *
 * <p>Everything else in this system sends and never listens. A parent's reply
 * reached the webhook, produced one {@code log.info} line, and was gone. This is
 * where it stops being gone.
 *
 * <p>Three rules from Meta shape every method here, and none of them is
 * negotiable:
 *
 * <ol>
 *   <li><b>Free text only inside the customer service window.</b> A business may
 *       write whatever it likes for 24 hours after the CUSTOMER's last message,
 *       and only a pre-approved template outside it. {@code last_inbound_at} is
 *       therefore not a display field - it is the permission, and
 *       {@link #windowOpen} is the only thing allowed to decide.</li>
 *   <li><b>Reply from the number they wrote to.</b> The window belongs to the
 *       (customer, business number) pair. Answering from a different line is a
 *       cold business-initiated message to somebody who never wrote to that
 *       line, and Meta treats it as one.</li>
 *   <li><b>Webhooks repeat.</b> Meta redelivers until it gets a 200, so every
 *       write here is idempotent on {@code wamid} - enforced by a unique index,
 *       not by hoping the retry never happens.</li>
 * </ol>
 *
 * <p>Writes from the webhook go through {@link JdbcTemplate} rather than
 * repositories for the reason {@code WhatsappWebhookService} already documents:
 * an event arrives with no session and therefore no tenant, and a Hibernate
 * {@code @TenantId} query would silently match nothing. The tenant is worked out
 * from the payload instead - see {@link #ownerOf}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WhatsappInboxService {

    /** Meta's customer service window: free text for this long after they write. */
    private static final String WINDOW = "24 hours";

    /** WhatsApp's own ceiling on one text message. */
    private static final int MAX_BODY = 4096;

    private final JdbcTemplate jdbc;
    private final CloudApiClient cloud;
    private final WhatsappThrottle throttle;
    private final WhatsappInstanceService instances;
    private final PlatformTransactionManager transactions;

    /**
     * One transaction per inbound message, suspending whatever the webhook is
     * already inside.
     *
     * <p>Not decoration. A batch of ten messages shares one webhook delivery,
     * and a failed statement in Postgres aborts the WHOLE transaction - after
     * which every later statement fails too, catch block or no catch block. So
     * "log it and carry on with the next message" is only true if each message
     * has its own transaction to lose.
     *
     * <p>Built here rather than annotated because {@code store} is called from
     * inside this same bean: a {@code @Transactional} on a self-invoked method
     * never reaches the proxy and does exactly nothing.
     */
    private TransactionTemplate perMessage;

    @jakarta.annotation.PostConstruct
    void initTransactionTemplate() {
        perMessage = new TransactionTemplate(transactions);
        perMessage.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ══ inbound ════════════════════════════════════════════════════════════

    /**
     * Files everything a webhook payload carried in the {@code messages} array.
     *
     * <p>Never throws. The webhook controller answers 200 to everything on
     * purpose - Meta disables a subscription that keeps failing - so a message
     * this method lets escape is a message lost for good, not one that comes
     * back on a retry. Each message therefore gets its own transaction and its
     * own catch, and one unreadable payload costs exactly itself.
     */
    public void ingest(JsonNode value) {
        JsonNode messages = value.path("messages");
        if (!messages.isArray() || messages.isEmpty()) {
            return;
        }
        String phoneNumberId = value.path("metadata").path("phone_number_id").asText(null);
        Map<String, String> names = profileNames(value.path("contacts"));

        for (JsonNode message : messages) {
            try {
                perMessage.executeWithoutResult(tx -> store(message, phoneNumberId, names));
            } catch (RuntimeException ex) {
                log.error("Could not store an inbound WhatsApp message: {}", ex.getMessage(), ex);
            }
        }
    }

    /** {@code wa_id -> the name that person has set on their own WhatsApp}. */
    private static Map<String, String> profileNames(JsonNode contacts) {
        Map<String, String> out = new HashMap<>();
        for (JsonNode contact : contacts) {
            String waId = contact.path("wa_id").asText(null);
            String name = contact.path("profile").path("name").asText(null);
            if (waId != null && name != null && !name.isBlank()) {
                out.put(waId, name);
            }
        }
        return out;
    }

    private void store(JsonNode message, String phoneNumberId, Map<String, String> names) {
        String waId = message.path("from").asText(null);
        String wamid = message.path("id").asText(null);
        if (waId == null || wamid == null) {
            return;
        }
        String phone = WaPhone.local(waId);
        UUID adminId = ownerOf(phoneNumberId, phone);
        if (adminId == null) {
            // Nobody in this system owns the number they wrote to, and nobody has
            // ever written to them from it. Filing this under a guessed workspace
            // would put a stranger's message in some teacher's inbox, so it is
            // recorded here and nowhere else.
            log.warn("Inbound WhatsApp message from {} to number {} belongs to no workspace",
                    phone, phoneNumberId);
            return;
        }

        UUID conversationId = conversation(adminId, phone, waId, names.get(waId), phoneNumberId);
        OffsetDateTime at = epochSeconds(message.path("timestamp").asText(null));
        String kind = message.path("type").asText("unsupported");
        Body body = readable(message, kind);

        int written = jdbc.update("""
                insert into wa_conversation_message
                    (admin_id, conversation_id, direction, wamid, kind, body,
                     media_id, media_mime, media_filename, status, occurred_at)
                values (?, ?, 'IN', ?, ?, ?, ?, ?, ?, 'RECEIVED', ?)
                on conflict (wamid) do nothing
                """, adminId, conversationId, wamid, kind, body.text(), body.mediaId(),
                body.mime(), body.fileName(), at);

        if (written == 0) {
            // Meta redelivered something already filed. Bumping the thread again
            // would re-raise the unread count on a message the teacher has read.
            return;
        }

        jdbc.update("""
                update wa_conversation
                   set last_inbound_at  = greatest(coalesce(last_inbound_at, ?::timestamptz), ?),
                       last_message_at  = greatest(last_message_at, ?),
                       last_direction   = 'IN',
                       last_preview     = ?,
                       unread           = unread + 1,
                       archived         = false,
                       phone_number_id  = coalesce(?, phone_number_id),
                       updated_at       = now()
                 where id = ?
                """, at, at, at, preview(body.text()), phoneNumberId, conversationId);

        log.info("Inbound WhatsApp {} from {} filed in workspace {}", kind, phone, adminId);
    }

    /**
     * Which workspace owns a message sent to one of our numbers.
     *
     * <p>A teacher's own number answers this outright. The platform's shared
     * number does not - several workspaces send through it - so the fallback is
     * "whoever last wrote to this person", which is both the only evidence
     * available and almost always right: a parent replies to the message they
     * were sent.
     *
     * @return the workspace, or null when this message belongs to nobody
     */
    private UUID ownerOf(String phoneNumberId, String phone) {
        if (phoneNumberId != null && !phoneNumberId.isBlank()) {
            List<UUID> owner = jdbc.query(
                    "select owner_admin_id from whatsapp_instance where phone_number_id = ?",
                    (rs, n) -> rs.getObject("owner_admin_id", UUID.class), phoneNumberId);
            if (!owner.isEmpty() && owner.get(0) != null) {
                return owner.get(0);
            }
        }
        List<UUID> lastSender = jdbc.query("""
                select admin_id from wa_message_log
                 where phone = ?
                 order by created_at desc
                 limit 1
                """, (rs, n) -> rs.getObject("admin_id", UUID.class), phone);
        return lastSender.isEmpty() ? null : lastSender.get(0);
    }

    /**
     * The thread for this person in this workspace, created if this is the first
     * time they have written.
     *
     * <p>{@code on conflict do update} rather than a select-then-insert: two
     * webhook deliveries of the same batch can land on two threads at once, and
     * the unique key is what makes one of them lose cleanly instead of both
     * inserting.
     */
    private UUID conversation(UUID adminId, String phone, String waId, String profileName,
            String phoneNumberId) {
        Match match = matchStudent(adminId, phone);
        return jdbc.queryForObject("""
                insert into wa_conversation
                    (admin_id, phone, wa_id, profile_name, phone_number_id,
                     student_id, student_name, student_code, contact_kind, last_message_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                on conflict (admin_id, phone) do update
                   set wa_id           = coalesce(excluded.wa_id, wa_conversation.wa_id),
                       profile_name    = coalesce(excluded.profile_name,
                                                  wa_conversation.profile_name),
                       phone_number_id = coalesce(excluded.phone_number_id,
                                                  wa_conversation.phone_number_id),
                       -- Re-matched every time: a number that belonged to nobody
                       -- when they first wrote becomes a student the moment that
                       -- student is added, and the thread should say so.
                       student_id      = coalesce(excluded.student_id,
                                                  wa_conversation.student_id),
                       student_name    = coalesce(excluded.student_name,
                                                  wa_conversation.student_name),
                       student_code    = coalesce(excluded.student_code,
                                                  wa_conversation.student_code),
                       contact_kind    = case when excluded.contact_kind = 'UNKNOWN'
                                              then wa_conversation.contact_kind
                                              else excluded.contact_kind end,
                       updated_at      = now()
                returning id
                """, UUID.class, adminId, phone, waId, profileName, phoneNumberId,
                match.studentId(), match.studentName(), match.studentCode(), match.kind());
    }

    /** Who this number belongs to on the roster, as far as the roster knows. */
    private record Match(UUID studentId, String studentName, String studentCode, String kind) {
        static final Match NONE = new Match(null, null, null, "UNKNOWN");
    }

    /**
     * The student behind a phone number.
     *
     * <p>The roster keeps two arrays per student - the student's own numbers and
     * the guardian's - and the difference matters to whoever reads the thread:
     * "أم أحمد" and "أحمد" are not the same correspondent.
     *
     * <p>Deliberately the same shape, and the same tie-break, as
     * {@code StudentRepository.findByAnyPhone}: a number held by both the
     * student and the guardian resolves as STUDENT, the narrower answer. The
     * two must agree, or one number would be a student in the send log and a
     * parent in the thread about that same send.
     *
     * <p>{@code ? = any(column)} binds a scalar against an array COLUMN, which
     * PgJDBC handles natively - unlike binding a Java array to {@code any(?)},
     * which needs an explicit {@code createArrayOf}.
     */
    private Match matchStudent(UUID adminId, String phone) {
        try {
            return jdbc.queryForObject("""
                    select id, name, serial,
                           case when ? = any (student_phones) then 'STUDENT' else 'PARENT' end
                                as kind
                      from students
                     where admin_id = ?
                       and deleted_at is null
                       and (? = any (student_phones) or ? = any (parent_phones))
                     order by case when ? = any (student_phones) then 0 else 1 end, created_at
                     limit 1
                    """, (rs, n) -> new Match(rs.getObject("id", UUID.class),
                            rs.getString("name"),
                            rs.getObject("serial") == null ? null
                                    : String.valueOf(rs.getInt("serial")),
                            rs.getString("kind")),
                    phone, adminId, phone, phone, phone);
        } catch (EmptyResultDataAccessException ex) {
            return Match.NONE;
        }
    }

    // ── making a message readable ──────────────────────────────────────────

    /** The readable text of an inbound message, plus its file if it has one. */
    private record Body(String text, String mediaId, String mime, String fileName) {
        static Body of(String text) {
            return new Body(text, null, null, null);
        }
    }

    /**
     * What a person actually sent, as a line someone can read.
     *
     * <p>WhatsApp carries far more than text - a location pin, a shared contact
     * card, a tap on a template's button, a reaction - and the list grows faster
     * than this file will. So every branch produces SOMETHING readable, and the
     * default says plainly that the type is not drawn here rather than showing an
     * empty bubble that looks like a bug.
     */
    private static Body readable(JsonNode m, String kind) {
        JsonNode node = m.path(kind);
        return switch (kind) {
            case "text" -> Body.of(m.path("text").path("body").asText(""));

            case "image", "video", "audio", "document", "sticker" -> new Body(
                    caption(node, kind),
                    node.path("id").asText(null),
                    node.path("mime_type").asText(null),
                    node.path("filename").asText(null));

            case "location" -> Body.of(location(node));

            case "contacts" -> Body.of("📇 " + contactNames(m.path("contacts")));

            // The customer tapped a quick-reply button on a template we sent.
            // The title is the whole message - there is no other text.
            case "button" -> Body.of(node.path("text").asText("زر"));

            case "interactive" -> Body.of(interactive(node));

            case "reaction" -> Body.of(node.path("emoji").asText("👍"));

            case "order" -> Body.of("🧾 طلب");

            // Meta's own word for "the customer changed their number" and the
            // like. It arrives with its own readable sentence.
            case "system" -> Body.of(m.path("system").path("body").asText("رسالة نظام"));

            default -> Body.of("نوع رسالة غير مدعوم (" + kind + ")");
        };
    }

    private static String caption(JsonNode node, String kind) {
        String caption = node.path("caption").asText(null);
        if (caption != null && !caption.isBlank()) {
            return caption;
        }
        return switch (kind) {
            case "image" -> "📷 صورة";
            case "video" -> "🎥 فيديو";
            case "audio" -> "🎤 رسالة صوتية";
            case "sticker" -> "🩹 ملصق";
            default -> {
                String name = node.path("filename").asText(null);
                yield "📎 " + (name == null || name.isBlank() ? "ملف" : name);
            }
        };
    }

    private static String location(JsonNode node) {
        String name = node.path("name").asText(null);
        String address = node.path("address").asText(null);
        if (name != null && !name.isBlank()) {
            return "📍 " + name + (address == null || address.isBlank() ? "" : " — " + address);
        }
        return "📍 موقع (" + node.path("latitude").asText("?") + ", "
                + node.path("longitude").asText("?") + ")";
    }

    private static String contactNames(JsonNode contacts) {
        List<String> names = new ArrayList<>();
        for (JsonNode contact : contacts) {
            String name = contact.path("name").path("formatted_name").asText(null);
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        return names.isEmpty() ? "جهة اتصال" : String.join("، ", names);
    }

    private static String interactive(JsonNode node) {
        JsonNode button = node.path("button_reply");
        if (!button.isMissingNode()) {
            return button.path("title").asText("زر");
        }
        JsonNode list = node.path("list_reply");
        if (!list.isMissingNode()) {
            return list.path("title").asText("اختيار");
        }
        return "رد تفاعلي";
    }

    // ── delivery receipts ──────────────────────────────────────────────────

    /**
     * A status update Meta sent about a message WE sent, applied to the thread.
     *
     * <p>Addressed by {@code wamid} and nothing else, for the same reason the
     * message log is: a webhook has no tenant, and the id is globally unique.
     * Each update guards on the column it fills being empty so a repeated or
     * reordered delivery cannot stamp a time after the read that already landed.
     */
    @Transactional
    public void applyStatus(String wamid, String state, OffsetDateTime at, Integer code,
            String reason) {
        switch (state) {
            case "sent" -> jdbc.update("""
                    update wa_conversation_message set status = 'SENT'
                     where wamid = ? and status = 'QUEUED'
                    """, wamid);
            case "delivered" -> jdbc.update("""
                    update wa_conversation_message
                       set status = 'DELIVERED', delivered_at = coalesce(delivered_at, ?)
                     where wamid = ? and status in ('QUEUED', 'SENT')
                    """, at, wamid);
            case "read" -> jdbc.update("""
                    update wa_conversation_message
                       set status = 'READ', read_at = coalesce(read_at, ?),
                           delivered_at = coalesce(delivered_at, ?)
                     where wamid = ? and status <> 'FAILED'
                    """, at, at, wamid);
            case "failed" -> jdbc.update("""
                    update wa_conversation_message
                       set status = 'FAILED', failure_code = ?, failure_reason = ?
                     where wamid = ?
                    """, code, reason, wamid);
            default -> { /* Meta added a state this build does not draw. */ }
        }
    }

    // ══ reading, for the screen ════════════════════════════════════════════

    /**
     * @param windowOpen  whether free text may still be sent - the composer is
     *                    enabled by this and nothing else
     * @param windowEndsAt when it closes. Null when it is already closed, or
     *                    when they have never written
     */
    public record Conversation(UUID id, String phone, String name, String profileName,
            UUID studentId, String studentName, String studentCode, String contactKind,
            OffsetDateTime lastMessageAt, OffsetDateTime lastInboundAt, String lastDirection,
            String lastPreview, int unread, boolean archived, boolean windowOpen,
            OffsetDateTime windowEndsAt) {}

    public record Message(UUID id, String direction, String kind, String body, boolean hasMedia,
            String mediaMime, String mediaFilename, String status, Integer failureCode,
            String failureReason, String sentByName, OffsetDateTime occurredAt,
            OffsetDateTime deliveredAt, OffsetDateTime readAt) {}

    private static final String CONVERSATION_COLUMNS = """
            select c.id, c.phone, c.wa_id, c.profile_name, c.student_id, c.student_name,
                   c.student_code, c.contact_kind, c.last_message_at, c.last_inbound_at,
                   c.last_direction, c.last_preview, c.unread, c.archived,
                   (c.last_inbound_at > now() - interval '%s') as window_open,
                   (c.last_inbound_at + interval '%s')         as window_ends_at
              from wa_conversation c
            """.formatted(WINDOW, WINDOW);

    private static final RowMapper<Conversation> CONVERSATION = (rs, n) -> {
        String student = rs.getString("student_name");
        String profile = rs.getString("profile_name");
        String phone = rs.getString("phone");
        boolean open = rs.getBoolean("window_open");
        return new Conversation(
                rs.getObject("id", UUID.class),
                phone,
                // The header's name, decided once here so the list, the thread
                // and the search all agree: the roster wins over a self-chosen
                // profile name, and a bare number is the last resort.
                student != null ? student : (profile != null ? profile : phone),
                profile,
                rs.getObject("student_id", UUID.class),
                student,
                rs.getString("student_code"),
                rs.getString("contact_kind"),
                rs.getObject("last_message_at", OffsetDateTime.class),
                rs.getObject("last_inbound_at", OffsetDateTime.class),
                rs.getString("last_direction"),
                rs.getString("last_preview"),
                rs.getInt("unread"),
                rs.getBoolean("archived"),
                open,
                open ? rs.getObject("window_ends_at", OffsetDateTime.class) : null);
    };

    /**
     * The thread list for the signed-in workspace.
     *
     * <p>{@code query} matches the roster name, the WhatsApp profile name and
     * the number - the three things somebody would type looking for a
     * conversation. Digits are normalised through {@link WaPhone} first, so
     * "+20 101…", "0101…" and "201…" all find the same thread.
     */
    @Transactional(readOnly = true)
    public List<Conversation> conversations(String query, boolean archived, int limit) {
        UUID adminId = requireTenant();
        String search = query == null ? "" : query.trim();
        if (search.isEmpty()) {
            return jdbc.query(CONVERSATION_COLUMNS + """
                     where c.admin_id = ? and c.archived = ?
                     order by c.last_message_at desc
                     limit ?
                    """, CONVERSATION, adminId, archived, limit);
        }
        String like = "%" + search + "%";
        String digits = WaPhone.local(search);
        return jdbc.query(CONVERSATION_COLUMNS + """
                 where c.admin_id = ? and c.archived = ?
                   and (c.student_name ilike ? or c.profile_name ilike ?
                        or c.phone like ? or c.student_code = ?)
                 order by c.last_message_at desc
                 limit ?
                """, CONVERSATION, adminId, archived, like, like,
                "%" + (digits.isEmpty() ? search : digits) + "%", search, limit);
    }

    @Transactional(readOnly = true)
    public Conversation conversation(UUID id) {
        UUID adminId = requireTenant();
        List<Conversation> rows = jdbc.query(
                CONVERSATION_COLUMNS + " where c.id = ? and c.admin_id = ?",
                CONVERSATION, id, adminId);
        if (rows.isEmpty()) {
            throw new BusinessRuleException("المحادثة غير موجودة");
        }
        return rows.get(0);
    }

    /**
     * The messages of one thread, oldest first.
     *
     * <p>Oldest first because that is reading order, and the screen scrolls to
     * the bottom. A newest-first page would have to be reversed by the client,
     * which is the sort of thing that gets forgotten on the second screen that
     * uses it.
     */
    @Transactional(readOnly = true)
    public List<Message> messages(UUID conversationId, int limit) {
        UUID adminId = requireTenant();
        return jdbc.query("""
                select m.id, m.direction, m.kind, m.body, m.media_id, m.media_mime,
                       m.media_filename, m.status, m.failure_code, m.failure_reason,
                       m.sent_by_name, m.occurred_at, m.delivered_at, m.read_at
                  from wa_conversation_message m
                  join wa_conversation c on c.id = m.conversation_id
                 where m.conversation_id = ? and c.admin_id = ?
                 order by m.occurred_at desc, m.created_at desc
                 limit ?
                """, (rs, n) -> new Message(
                        rs.getObject("id", UUID.class),
                        rs.getString("direction"),
                        rs.getString("kind"),
                        rs.getString("body"),
                        rs.getString("media_id") != null,
                        rs.getString("media_mime"),
                        rs.getString("media_filename"),
                        rs.getString("status"),
                        rs.getObject("failure_code", Integer.class),
                        rs.getString("failure_reason"),
                        rs.getString("sent_by_name"),
                        rs.getObject("occurred_at", OffsetDateTime.class),
                        rs.getObject("delivered_at", OffsetDateTime.class),
                        rs.getObject("read_at", OffsetDateTime.class)),
                conversationId, adminId, limit)
                .reversed();
    }

    /** Unread inbound messages across every thread - the badge on the tab. */
    @Transactional(readOnly = true)
    public int unreadTotal() {
        UUID adminId = TenantContext.get();
        if (adminId == null) {
            return 0;
        }
        Integer total = jdbc.queryForObject("""
                select coalesce(sum(unread), 0) from wa_conversation
                 where admin_id = ? and archived = false
                """, Integer.class, adminId);
        return total == null ? 0 : total;
    }

    // ══ acting ═════════════════════════════════════════════════════════════

    /**
     * Marks a thread read here and at WhatsApp.
     *
     * <p>The blue ticks are a courtesy to the person waiting, so they are sent
     * best-effort: {@link CloudApiClient#markRead} swallows its own failures and
     * the local count is zeroed either way. A thread that would not open because
     * Meta was slow would be a worse bug than a missing tick.
     */
    @Transactional
    public void markRead(UUID conversationId) {
        UUID adminId = requireTenant();
        int touched = jdbc.update("""
                update wa_conversation set unread = 0, updated_at = now()
                 where id = ? and admin_id = ? and unread > 0
                """, conversationId, adminId);
        if (touched == 0) {
            return;
        }
        List<String[]> latest = jdbc.query("""
                select c.phone_number_id, m.wamid
                  from wa_conversation_message m
                  join wa_conversation c on c.id = m.conversation_id
                 where m.conversation_id = ? and m.direction = 'IN' and m.wamid is not null
                 order by m.occurred_at desc
                 limit 1
                """, (rs, n) -> new String[] {rs.getString(1), rs.getString(2)}, conversationId);
        if (!latest.isEmpty()) {
            cloud.markRead(latest.get(0)[0], latest.get(0)[1]);
        }
    }

    @Transactional
    public void archive(UUID conversationId, boolean archived) {
        UUID adminId = requireTenant();
        jdbc.update("""
                update wa_conversation set archived = ?, updated_at = now()
                 where id = ? and admin_id = ?
                """, archived, conversationId, adminId);
    }

    /**
     * Opens (or finds) the thread for a phone number, so a teacher can look at
     * the history of somebody who has not written today.
     *
     * <p>Creating a thread does NOT create permission to write in it. The
     * composer stays closed until that person sends something, because that is
     * the only event that opens Meta's window.
     */
    @Transactional
    public Conversation open(String rawPhone) {
        UUID adminId = requireTenant();
        String phone = WaPhone.local(rawPhone);
        if (phone.length() < 10) {
            throw new BusinessRuleException("رقم غير صالح");
        }
        conversation(adminId, phone, WaPhone.international(phone), null, null);
        List<Conversation> rows = jdbc.query(
                CONVERSATION_COLUMNS + " where c.admin_id = ? and c.phone = ?",
                CONVERSATION, adminId, phone);
        return rows.get(0);
    }

    /**
     * Sends one free-form message inside the window.
     *
     * <p>Every refusal below is checked HERE rather than in the UI. The screen
     * disables the composer when the window is closed, but the window can close
     * between the page painting and the button being pressed - a thread left
     * open on a second monitor is exactly the case - and Meta's rejection of a
     * message sent one second late is not a nice error message.
     */
    @Transactional
    public Message send(UUID conversationId, String rawBody, String senderName) {
        UUID adminId = requireTenant();
        String body = rawBody == null ? "" : rawBody.trim();
        if (body.isEmpty()) {
            throw new BusinessRuleException("اكتب الرسالة أولاً");
        }
        if (body.length() > MAX_BODY) {
            throw new BusinessRuleException(
                    "الرسالة أطول من الحد المسموح على واتساب (" + MAX_BODY + " حرف)");
        }

        Target target = target(conversationId, adminId);
        if (!target.windowOpen()) {
            throw new BusinessRuleException(
                    "انتهت مدة الرد الحر (٢٤ ساعة من آخر رسالة من هذا الرقم). "
                            + "لا يمكن إرسال نص حر الآن — لن يقبله واتساب.");
        }

        // The platform's grant is checked; the workspace's own pause switch is
        // deliberately NOT. That switch stops the automated flood - attendance,
        // absence, grades - and a teacher who paused it has not thereby decided
        // to stop answering a parent who is mid-conversation with them. Leaving
        // a person on "seen" for 24 hours is not what pausing the broadcasts
        // meant.
        if (!instances.enabledFor(adminId)) {
            throw new BusinessRuleException("ميزة واتساب غير مُفعّلة لحسابك من قبل الإدارة");
        }
        // The number they wrote to, not whichever number happens to be free: the
        // 24-hour window belongs to that pair. Falling back to the resolved
        // number only covers a thread created before this column existed.
        String phoneNumberId = target.phoneNumberId() != null
                ? target.phoneNumberId()
                : instances.resolve().phoneNumberId();
        if (phoneNumberId == null) {
            throw new BusinessRuleException("لا يوجد رقم واتساب مُفعّل للإرسال");
        }

        // Same pacing as every other send: 80 messages per second overall, and
        // no more than one every six seconds to the same person. A conversation
        // is exactly where the second limit gets hit - two quick replies to one
        // parent are two messages to one number.
        throttle.acquire(target.phone());

        CloudApiClient.SendResult result = cloud.sendText(phoneNumberId,
                target.waId() == null ? target.phone() : target.waId(), body);

        UUID id = jdbc.queryForObject("""
                insert into wa_conversation_message
                    (admin_id, conversation_id, direction, wamid, kind, body, status,
                     failure_code, failure_reason, sent_by_user_id, sent_by_name, occurred_at)
                values (?, ?, 'OUT', ?, 'text', ?, ?, ?, ?, ?, ?, now())
                returning id
                """, UUID.class, adminId, conversationId, result.messageId(), body,
                result.sent() ? "SENT" : "FAILED",
                result.sent() ? null : result.errorCode(),
                result.sent() ? null : result.failureReason(),
                AuthenticatedUser.currentId(), senderName);

        // A failed send still moves the thread: the red bubble is part of the
        // conversation, and hiding it would leave the teacher believing a
        // message went that never did.
        jdbc.update("""
                update wa_conversation
                   set last_message_at = now(), last_direction = 'OUT',
                       last_preview = ?, updated_at = now()
                 where id = ?
                """, preview(body), conversationId);

        if (!result.sent()) {
            log.warn("Inbox reply to {} refused by WhatsApp: code={} reason={}",
                    target.phone(), result.errorCode(), result.failureReason());
        }

        return new Message(id, "OUT", "text", body, false, null, null,
                result.sent() ? "SENT" : "FAILED",
                result.sent() ? null : result.errorCode(),
                result.sent() ? null : result.failureReason(),
                senderName, OffsetDateTime.now(), null, null);
    }

    /** The addressing facts a send needs, read in one query. */
    private record Target(String phone, String waId, String phoneNumberId, boolean windowOpen) {}

    private Target target(UUID conversationId, UUID adminId) {
        List<Target> rows = jdbc.query("""
                select phone, wa_id, phone_number_id,
                       (last_inbound_at > now() - interval '%s') as window_open
                  from wa_conversation
                 where id = ? and admin_id = ?
                """.formatted(WINDOW),
                (rs, n) -> new Target(rs.getString("phone"), rs.getString("wa_id"),
                        rs.getString("phone_number_id"), rs.getBoolean("window_open")),
                conversationId, adminId);
        if (rows.isEmpty()) {
            throw new BusinessRuleException("المحادثة غير موجودة");
        }
        return rows.get(0);
    }

    // ══ media ══════════════════════════════════════════════════════════════

    /** A file from a thread, ready to be written to the response. */
    public record MediaFile(byte[] content, String mime, String fileName) {}

    /**
     * The file attached to one message, fetched from Meta the first time and
     * kept afterwards.
     *
     * <p>Not fetched at webhook time on purpose: Meta gives a handler seconds
     * before it treats the delivery as failed and starts redelivering, and a
     * 16 MB video does not fit in that. So the id is stored immediately and the
     * bytes are pulled by the first request that actually wants to look at
     * them.
     *
     * <p>Meta deletes inbound media after 30 days. A file nobody opened in that
     * time is genuinely gone - {@link BusinessRuleException} says so rather than
     * returning an empty file that looks corrupt.
     */
    @Transactional
    public MediaFile media(UUID messageId) {
        UUID adminId = requireTenant();
        List<MediaFile> cached = jdbc.query("""
                select content, mime, filename from wa_media
                 where message_id = ? and admin_id = ?
                """, (rs, n) -> new MediaFile(rs.getBytes("content"), rs.getString("mime"),
                        rs.getString("filename")), messageId, adminId);
        if (!cached.isEmpty()) {
            return cached.get(0);
        }

        List<String[]> row = jdbc.query("""
                select m.media_id, m.media_mime, m.media_filename
                  from wa_conversation_message m
                 where m.id = ? and m.admin_id = ? and m.media_id is not null
                """, (rs, n) -> new String[] {rs.getString(1), rs.getString(2), rs.getString(3)},
                messageId, adminId);
        if (row.isEmpty()) {
            throw new BusinessRuleException("لا يوجد ملف مرفق بهذه الرسالة");
        }

        CloudApiClient.Media file = cloud.downloadMedia(row.get(0)[0]);
        if (file == null) {
            throw new BusinessRuleException(
                    "الملف لم يعد متاحًا على واتساب (تُحذف المرفقات بعد ٣٠ يومًا)");
        }
        String mime = file.mime() != null ? file.mime() : row.get(0)[1];
        String name = file.fileName() != null ? file.fileName() : row.get(0)[2];

        jdbc.update("""
                insert into wa_media (message_id, admin_id, mime, filename, content, size_bytes)
                values (?, ?, ?, ?, ?, ?)
                on conflict (message_id) do nothing
                """, messageId, adminId, mime, name, file.content(), file.content().length);
        jdbc.update("update wa_conversation_message set media_size = ? where id = ?",
                file.content().length, messageId);

        return new MediaFile(file.content(), mime, name);
    }

    // ══ plumbing ═══════════════════════════════════════════════════════════

    private static UUID requireTenant() {
        UUID adminId = TenantContext.get();
        if (adminId == null) {
            // The super admin console has no workspace, so it has no inbox. Said
            // plainly rather than returning an empty list that reads as "nobody
            // has ever written to you".
            throw new BusinessRuleException("الرسائل تخص مساحة عمل مدرّس، ولا تظهر في لوحة المنصة");
        }
        return adminId;
    }

    /** Short enough for a list row, long enough to recognise the message. */
    private static String preview(String body) {
        if (body == null) {
            return null;
        }
        String flat = body.replaceAll("\\s+", " ").trim();
        return flat.length() <= 120 ? flat : flat.substring(0, 119) + "…";
    }

    private static OffsetDateTime epochSeconds(String value) {
        if (value == null || value.isBlank()) {
            return OffsetDateTime.now();
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(value)).atOffset(ZoneOffset.UTC);
        } catch (NumberFormatException ex) {
            return OffsetDateTime.now();
        }
    }
}
