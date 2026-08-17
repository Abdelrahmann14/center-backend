package com.center.notification.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.admin.repository.SuperAdminRepository;
import com.center.common.enums.AcademicTrack;
import com.center.common.enums.Gender;
import com.center.common.enums.NotificationType;
import com.center.common.enums.Religion;
import com.center.common.exception.BusinessRuleException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.common.tenant.TenantContext;
import com.center.notification.dto.AdminBroadcastRequest;
import com.center.notification.dto.BroadcastResult;
import com.center.notification.dto.MessageTemplateCreateRequest;
import com.center.notification.dto.MessageTemplateResponse;
import com.center.notification.dto.MessageTemplateUpdateRequest;
import com.center.notification.dto.MessagingRecipient;
import com.center.notification.dto.OutgoingMessageResponse;
import com.center.notification.entity.MessageTemplate;
import com.center.notification.entity.OutgoingMessage;
import com.center.notification.repository.MessageTemplateRepository;
import com.center.notification.repository.NotificationRepository;
import com.center.notification.repository.OutgoingMessageRepository;
import com.center.notification.service.VariableCatalog.Variable;
import com.center.parent.entity.Parent;
import com.center.parent.repository.ParentRepository;
import com.center.settings.service.SettingsService;
import com.center.student.dto.StudentFilter;
import com.center.student.entity.Student;
import com.center.student.repository.StudentRepository;
import com.center.student.specification.StudentSpecifications;
import com.center.user.repository.UserRepository;
import com.center.whatsapp.service.GreenApiClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminMessagingServiceImpl implements AdminMessagingService {

    private static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");
    private static final Pattern MARKER = Pattern.compile("\\{([^{}]+)\\}");
    private static final Map<DayOfWeek, String> ARABIC_DAYS = Map.of(
            DayOfWeek.SATURDAY, "السبت", DayOfWeek.SUNDAY, "الأحد", DayOfWeek.MONDAY, "الاثنين",
            DayOfWeek.TUESDAY, "الثلاثاء", DayOfWeek.WEDNESDAY, "الأربعاء", DayOfWeek.THURSDAY, "الخميس",
            DayOfWeek.FRIDAY, "الجمعة");

    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final SuperAdminRepository superAdminRepository;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final OutgoingMessageRepository outgoingMessageRepository;
    private final MessageTemplateRepository messageTemplateRepository;
    private final SettingsService settingsService;
    private final UserRepository userRepository;
    private final GreenApiClient greenApiClient;

    /**
     * Routes the transactional halves of {@link #broadcast} through the proxy;
     * a plain {@code this.} call would bypass it and put the WhatsApp fan-out
     * back inside the transaction it was just taken out of.
     */
    @Autowired
    @Lazy
    private AdminMessagingServiceImpl self;

    private static UUID adminId() {
        UUID id = TenantContext.get();
        if (id == null) {
            throw new BusinessRuleException("هذه الصفحة متاحة لحسابات المدرّسين فقط");
        }
        return id;
    }

    // ── Broadcast ─────────────────────────────────────────────────────────

    /** One WhatsApp message the broadcast still owes, resolved but not yet sent. */
    private record PendingSend(String phone, String body) {
    }

    /** What {@link #planBroadcast} produced: the record row and the sends it owes. */
    private record Planned(UUID outgoingId, int recipients, List<PendingSend> sends) {
    }

    /**
     * Deliberately NOT {@code @Transactional} - it used to be, and that put a
     * WhatsApp round trip per recipient inside a single write transaction. A
     * broadcast to a 500-parent grade therefore held one of eight pooled
     * connections across 500 sequential calls to a third party, on a request
     * thread, while every other request in the system queued behind it. It is
     * also the one path in the messaging feature that the earlier split missed.
     *
     * <p>Now: resolve and persist in one short transaction, send with nothing
     * held, then record the count in a second short one. The in-app
     * notifications - the part that must not be lost - commit before the first
     * WhatsApp call is made, which is strictly safer than before.
     */
    @Override
    public BroadcastResult broadcast(AdminBroadcastRequest request) {
        Planned planned = self.planBroadcast(request);
        int whatsappSent = 0;
        for (PendingSend send : planned.sends()) {
            try {
                greenApiClient.sendText("broadcast", send.phone(), send.body());
                whatsappSent++;
            } catch (RuntimeException e) {
                log.warn("Admin WhatsApp broadcast to a recipient failed: {}", e.getMessage());
            }
        }
        self.recordSent(planned.outgoingId(), whatsappSent);
        return new BroadcastResult(planned.recipients(), whatsappSent);
    }

    /** Persist the record and every in-app notification; collect what to send. */
    @Transactional
    public Planned planBroadcast(AdminBroadcastRequest request) {
        UUID adminId = adminId();
        Set<UUID> recipients = resolveRecipients(request);
        recipients.remove(null);
        if (recipients.isEmpty()) {
            throw new BusinessRuleException("لا يوجد مستلمون مطابقون للتحديد");
        }
        // Sent AS THE TEACHER, not as the platform: recipients see the teacher's own
        // name and profile photo, since this is their workspace's message.
        String sender = teacherName(adminId);
        String rawTitle = request.title().strip();
        String rawBody = request.body().strip();

        Map<String, String> global = globalVars(sender);
        Map<UUID, Map<String, String>> perUser = recipientVars(recipients);
        Map<String, String> base = new HashMap<>();
        for (String key : VariableCatalog.keys()) {
            base.put(key, "");
        }

        OutgoingMessage record = new OutgoingMessage();
        record.setAdminId(adminId);
        record.setChannel(request.whatsapp() ? "whatsapp" : "notification");
        record.setSender(sender);
        record.setTitle(rawTitle);
        record.setBody(rawBody);
        record.setAudience(audienceSummary(request));
        record.setRecipients(recipients.size());
        record.setWhatsappSent(0);
        outgoingMessageRepository.save(record);
        UUID outgoingId = record.getId();

        List<PendingSend> sends = new java.util.ArrayList<>();
        for (UUID recipientId : recipients) {
            Map<String, String> vars = new HashMap<>(base);
            vars.putAll(global);
            Map<String, String> mine = perUser.get(recipientId);
            if (mine != null) {
                vars.putAll(mine);
            }
            String title = stripMarkers(interpolate(rawTitle, vars), false);
            String rendered = interpolate(rawBody, vars);
            String body = stripMarkers(rendered, false);
            notificationService.notifyFrom(recipientId, adminId, sender, NotificationType.SYSTEM_CENTER,
                    title, body, null, outgoingId);

            if (request.whatsapp() && mine != null) {
                String phone = firstNonBlank(mine.get("student.phone"), mine.get("parent.phone"));
                if (phone != null) {
                    sends.add(new PendingSend(phone, stripMarkers(rendered, true)));
                }
            }
        }
        return new Planned(outgoingId, recipients.size(), sends);
    }

    /** Stamp the delivered count once the sending is over. */
    @Transactional
    public void recordSent(UUID outgoingId, int whatsappSent) {
        outgoingMessageRepository.findById(outgoingId).ifPresent(row -> {
            row.setWhatsappSent(whatsappSent);
            outgoingMessageRepository.save(row);
        });
    }

    /** Union of every selected facet, all scoped to the admin's own workspace. */
    private Set<UUID> resolveRecipients(AdminBroadcastRequest req) {
        Set<UUID> set = new HashSet<>();
        if (req.religions() != null) {
            for (Religion r : req.religions()) {
                addStudents(filter(f -> f.religion(r)), set);
            }
        }
        if (req.genders() != null) {
            for (Gender g : req.genders()) {
                addStudents(filter(f -> f.gender(g)), set);
            }
        }
        if (req.grades() != null) {
            for (String grade : req.grades()) {
                addStudents(filter(f -> f.grade(grade)), set);
            }
        }
        if (req.groupIds() != null) {
            for (UUID gid : req.groupIds()) {
                addStudents(filter(f -> f.groupId(gid)), set);
            }
        }
        if (req.academicTrack() != null) {
            addStudents(filter(f -> f.academicTrack(req.academicTrack())), set);
        }
        if (req.studentIds() != null && !req.studentIds().isEmpty()) {
            for (Student s : studentRepository.findAllById(req.studentIds())) {
                if (s.getUserId() != null) {
                    set.add(s.getUserId());
                }
            }
        }
        if (req.parentIds() != null && !req.parentIds().isEmpty()) {
            for (Parent p : parentRepository.findAllById(req.parentIds())) {
                if (p.getUserId() != null) {
                    set.add(p.getUserId());
                }
            }
        }
        return set;
    }

    private void addStudents(StudentFilter filter, Set<UUID> set) {
        for (Student s : studentRepository.findAll(StudentSpecifications.matching(filter))) {
            if (s.getUserId() != null) {
                set.add(s.getUserId());
            }
        }
    }

    // ── Recipient pickers ─────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<MessagingRecipient> searchStudents(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return studentRepository.findAll(StudentSpecifications.matching(filter(f -> f.search(q.strip())))).stream()
                .limit(20)
                .map(s -> new MessagingRecipient(s.getId(), s.getName(),
                        s.getGrade() == null ? "" : s.getGrade()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessagingRecipient> searchParents(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return parentRepository.searchForAdmin(adminId(), q.strip()).stream()
                .limit(20)
                .map(p -> new MessagingRecipient(p.getId(), p.getName(), p.getPhone()))
                .toList();
    }

    // ── History ───────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<OutgoingMessageResponse> outgoing() {
        return outgoingMessageRepository.findTop50ByAdminIdOrderByCreatedAtDesc(adminId()).stream()
                .map(m -> new OutgoingMessageResponse(m.getId(), m.getChannel(), m.getSender(),
                        m.getTitle(), m.getBody(), m.getAudience(), m.getRecipients(),
                        m.getWhatsappSent(), m.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public void deleteOutgoing(UUID id) {
        OutgoingMessage record = outgoingMessageRepository.findById(id)
                .filter(m -> adminId().equals(m.getAdminId()))
                .orElseThrow(() -> new ResourceNotFoundException("الإشعار غير موجود"));
        notificationRepository.deleteByOutgoingId(record.getId());
        outgoingMessageRepository.delete(record);
    }

    // ── Templates (own custom + read-only system) ─────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<MessageTemplateResponse> templates() {
        // Only the admin's own custom templates. The automatic system messages
        // (login / signup / password / verification / link) are platform-wide and
        // managed by the super admin alone.
        List<MessageTemplateResponse> out = new ArrayList<>();
        for (MessageTemplate t : messageTemplateRepository.findByAdminIdOrderByUpdatedAtDesc(adminId())) {
            out.add(toResponse(t));
        }
        return out;
    }

    @Override
    @Transactional
    public MessageTemplateResponse createTemplate(MessageTemplateCreateRequest request) {
        boolean notification = "notification".equals(request.channel());
        MessageTemplate t = new MessageTemplate();
        t.setCode("custom_" + UUID.randomUUID().toString().substring(0, 8));
        t.setAdminId(adminId());
        t.setName(request.name().strip());
        t.setChannel(request.channel());
        t.setTitle(notification && request.title() != null && !request.title().isBlank()
                ? request.title().strip() : null);
        t.setBody(request.body().strip());
        t.setEnabled(true);
        t.setSystem(false);
        t.setUpdatedAt(OffsetDateTime.now());
        messageTemplateRepository.save(t);
        return toResponse(t);
    }

    @Override
    @Transactional
    public MessageTemplateResponse updateTemplate(String code, MessageTemplateUpdateRequest request) {
        MessageTemplate t = ownTemplate(code);
        if (t.getTitle() != null) {
            t.setTitle(request.title() == null ? null : request.title().strip());
        }
        t.setBody(request.body().strip());
        t.setUpdatedAt(OffsetDateTime.now());
        messageTemplateRepository.save(t);
        return toResponse(t);
    }

    @Override
    @Transactional
    public void deleteTemplate(String code) {
        messageTemplateRepository.delete(ownTemplate(code));
    }

    @Override
    @Transactional
    public MessageTemplateResponse setTemplateEnabled(String code, boolean enabled) {
        MessageTemplate t = ownTemplate(code);
        t.setEnabled(enabled);
        t.setUpdatedAt(OffsetDateTime.now());
        messageTemplateRepository.save(t);
        return toResponse(t);
    }

    private MessageTemplate ownTemplate(String code) {
        return messageTemplateRepository.findByCodeAndAdminId(code, adminId())
                .orElseThrow(() -> new ResourceNotFoundException("القالب غير موجود"));
    }

    @Override
    public List<Variable> variables() {
        return VariableCatalog.ALL;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** A single-facet StudentFilter built by mutating one field of an empty base. */
    private static StudentFilter filter(java.util.function.UnaryOperator<FilterBuilder> b) {
        return b.apply(new FilterBuilder()).build();
    }

    /** Tiny mutable builder so single-facet filters read clearly. */
    private static final class FilterBuilder {
        private String search;
        private String grade;
        private UUID groupId;
        private Gender gender;
        private AcademicTrack academicTrack;
        private Religion religion;

        FilterBuilder search(String v) { this.search = v; return this; }
        FilterBuilder grade(String v) { this.grade = v; return this; }
        FilterBuilder groupId(UUID v) { this.groupId = v; return this; }
        FilterBuilder gender(Gender v) { this.gender = v; return this; }
        FilterBuilder academicTrack(AcademicTrack v) { this.academicTrack = v; return this; }
        FilterBuilder religion(Religion v) { this.religion = v; return this; }

        StudentFilter build() {
            return new StudentFilter(
                    search, null, null, null, grade, groupId, gender, academicTrack, null, religion,
                    null);
        }
    }

    /** The teacher (workspace admin) display name, used as the message sender. */
    private String teacherName(UUID adminId) {
        return userRepository.findById(adminId)
                .map(u -> u.getUsername())
                .filter(n -> n != null && !n.isBlank())
                .orElseGet(settingsService::senderName);
    }

    private Map<String, String> globalVars(String sender) {
        LocalDate date = LocalDate.now(CAIRO);
        LocalTime time = LocalTime.now(CAIRO);
        String d = date.toString();
        String t = String.format("%02d:%02d", time.getHour(), time.getMinute());
        Map<String, String> map = new HashMap<>();
        map.put("date", d);
        map.put("time", t);
        map.put("now", d + " " + t);
        map.put("day", ARABIC_DAYS.get(date.getDayOfWeek()));
        map.put("sender", sender);
        return map;
    }

    private Map<UUID, Map<String, String>> recipientVars(Set<UUID> recipients) {
        String ids = recipients.stream().map(UUID::toString).collect(Collectors.joining(","));
        Map<UUID, Map<String, String>> perUser = new HashMap<>();
        for (SuperAdminRepository.StudentVarRow r : superAdminRepository.findStudentVars(ids)) {
            Map<String, String> m = perUser.computeIfAbsent(r.getUserId(), k -> new HashMap<>());
            m.put("student.name", nz(r.getName()));
            m.put("student.phone", nz(r.getPhone()));
            m.put("student.grade", nz(r.getGrade()));
            m.put("student.serial", r.getSerial() == null ? "" : String.valueOf(r.getSerial()));
            m.put("parent.phone", nz(r.getParentPhone()));
        }
        for (SuperAdminRepository.SimpleUserRow r : superAdminRepository.findParentVars(ids)) {
            Map<String, String> m = perUser.computeIfAbsent(r.getUserId(), k -> new HashMap<>());
            m.put("parent.name", nz(r.getName()));
            m.put("parent.phone", nz(r.getPhone()));
        }
        return perUser;
    }

    private static String interpolate(String template, Map<String, String> vars) {
        if (template == null) {
            return null;
        }
        String out = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    private static String stripMarkers(String text, boolean whatsapp) {
        if (text == null) {
            return null;
        }
        return MARKER.matcher(text).replaceAll(whatsapp ? "*$1*" : "$1");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b != null && !b.isBlank() ? b : null;
    }

    private MessageTemplateResponse toResponse(MessageTemplate t) {
        return new MessageTemplateResponse(t.getCode(), t.getName(), t.getChannel(),
                t.getTitle(), t.getBody(), t.getVariables(), t.isEnabled(), t.isSystem(),
                t.getCreatedAt(), t.getCreatedBy(), t.getUpdatedAt(), t.getUpdatedBy());
    }

    private static String audienceSummary(AdminBroadcastRequest req) {
        List<String> parts = new ArrayList<>();
        if (req.religions() != null) {
            for (Religion r : req.religions()) {
                parts.add(r == Religion.MUSLIM ? "المسلمون" : "المسيحيون");
            }
        }
        if (req.genders() != null) {
            for (Gender g : req.genders()) {
                parts.add(g == Gender.MALE ? "الذكور" : "الإناث");
            }
        }
        if (req.grades() != null) {
            parts.addAll(req.grades());
        }
        if (req.groupIds() != null && !req.groupIds().isEmpty()) {
            parts.add("مجموعات محددة (" + req.groupIds().size() + ")");
        }
        if (req.academicTrack() != null) {
            parts.add(req.academicTrack().getValue());
        }
        if (req.studentIds() != null && !req.studentIds().isEmpty()) {
            parts.add("طلاب محددون (" + req.studentIds().size() + ")");
        }
        if (req.parentIds() != null && !req.parentIds().isEmpty()) {
            parts.add("أولياء أمور محددون (" + req.parentIds().size() + ")");
        }
        return String.join("، ", parts);
    }
}
