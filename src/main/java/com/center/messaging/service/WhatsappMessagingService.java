package com.center.messaging.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.UnaryOperator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.common.enums.AcademicTrack;
import com.center.common.enums.AutomationType;
import com.center.common.enums.Gender;
import com.center.common.enums.MessageAudience;
import com.center.common.enums.RegistrationStatus;
import com.center.common.enums.Religion;
import com.center.common.exception.BusinessRuleException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.common.tenant.TenantContext;
import com.center.group.entity.Group;
import com.center.group.repository.GroupRepository;
import com.center.lecture.entity.Lecture;
import com.center.lecture.repository.LectureRepository;
import com.center.messaging.dto.AttendanceOptinResponse;
import com.center.messaging.dto.AttendanceWhatsappCheck;
import com.center.messaging.dto.AutomationResponse;
import com.center.messaging.dto.AutomationUpdateRequest;
import com.center.messaging.dto.LectureAbsentee;
import com.center.messaging.dto.LectureMessageStatus;
import com.center.messaging.dto.LecturePendingCounts;
import com.center.messaging.dto.VariantResponse;
import com.center.messaging.dto.VariantUpdateRequest;
import com.center.messaging.dto.WhatsappMessageLogResponse;
import com.center.messaging.dto.WhatsappSendRequest;
import com.center.messaging.dto.WhatsappSendResult;
import com.center.messaging.entity.AttendanceAutoOptin;
import com.center.messaging.entity.MessageAutomation;
import com.center.messaging.entity.MessageVariant;
import com.center.messaging.entity.WhatsappMessageLog;
import com.center.messaging.repository.AttendanceAutoOptinRepository;
import com.center.messaging.repository.MessageAutomationRepository;
import com.center.messaging.repository.MessageVariantRepository;
import com.center.messaging.repository.WhatsappMessageLogRepository;
import com.center.common.enums.LinkStatus;
import com.center.parent.entity.Parent;
import com.center.parent.repository.ParentRepository;
import com.center.parent.repository.ParentStudentLinkRepository;
import com.center.registration.entity.Registration;
import com.center.registration.repository.RegistrationRepository;
import com.center.settings.service.SettingsService;
import com.center.student.dto.StudentFilter;
import com.center.student.entity.Student;
import com.center.student.repository.StudentRepository;
import com.center.student.specification.StudentSpecifications;
import com.center.user.repository.UserRepository;
import com.center.whatsapp.service.GreenApiClient;

import lombok.RequiredArgsConstructor;

/**
 * The Messages page's server side: the automated-message config (attendance /
 * absence), AI variant generation, the manual WhatsApp send, the per-lesson
 * attendance/absence sends triggered from the Lessons page, and the send log.
 * All WhatsApp only - nothing here touches the in-app notification inbox.
 */
@Service
@RequiredArgsConstructor
public class WhatsappMessagingService {

    private static final int ALTERNATIVES = 3;

    private final MessageAutomationRepository automationRepository;
    private final MessageVariantRepository variantRepository;
    private final WhatsappMessageLogRepository logRepository;
    private final AttendanceAutoOptinRepository optinRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final ParentStudentLinkRepository parentLinkRepository;
    private final RegistrationRepository registrationRepository;
    private final GroupRepository groupRepository;
    private final LectureRepository lectureRepository;
    private final UserRepository userRepository;
    private final SettingsService settingsService;
    private final AiVariantClient aiVariantClient;
    private final WhatsappLogSender logSender;
    private final GreenApiClient greenApiClient;
    private final com.center.student.service.StudentBarcodeService barcodeService;
    private final com.center.student.service.StudentReportService reportService;

    /**
     * This service through its own Spring proxy.
     *
     * <p>A broadcast is now "read in a transaction, then send outside it", and the
     * read half has to actually START a transaction. A plain {@code this.plan...()}
     * call never reaches the proxy, so {@code @Transactional} on it would be
     * silently ignored and the lazy loads inside would fail. {@code @Lazy} breaks
     * the self-reference cycle at construction time.
     */
    @Autowired
    @Lazy
    private WhatsappMessagingService self;

    private static UUID adminId() {
        UUID id = TenantContext.get();
        if (id == null) {
            throw new BusinessRuleException("هذه الصفحة متاحة لحسابات المدرّسين فقط");
        }
        return id;
    }

    // ── Automated messages ────────────────────────────────────────────────

    @Transactional
    public List<AutomationResponse> automations() {
        return List.of(
                toResponse(ensure(AutomationType.ATTENDANCE)),
                toResponse(ensure(AutomationType.ABSENCE)),
                toResponse(ensure(AutomationType.NEW_STUDENT)),
                toResponse(ensure(AutomationType.EXAM_GRADE)),
                toResponse(ensure(AutomationType.REPORT)));
    }

    @Transactional
    public AutomationResponse updateAutomation(AutomationType type, AutomationUpdateRequest req) {
        MessageAutomation a = ensure(type);
        // The recipient is chosen per template on the Messages page (parent, student,
        // or both). The week window is unused: attendance auto-send is gated per
        // (lecture, group) on the registration page, and absence is sent from the
        // Lessons page button.
        if (req.audience() != null) {
            a.setAudience(req.audience());
        }
        a.setEnabled(true);
        a.setWeekStartDay(null);
        a.setWeekEndDay(null);
        automationRepository.save(a);

        if (req.base() != null) {
            String base = req.base().strip();
            boolean asImage = Boolean.TRUE.equals(req.baseSendAsImage());
            MessageVariant baseRow = variantRepository.findByAutomationIdOrderBySortOrder(a.getId()).stream()
                    .filter(v -> v.getSortOrder() == 0).findFirst().orElse(null);
            if (baseRow != null) {
                baseRow.setBody(base);
                baseRow.setSendAsImage(asImage);
                variantRepository.save(baseRow);
            } else if (!base.isEmpty()) {
                MessageVariant fresh = newVariant(a.getId(), base, 0);
                fresh.setSendAsImage(asImage);
                variantRepository.save(fresh);
            }
        }
        return toResponse(a);
    }

    @Transactional
    public AutomationResponse generateVariants(AutomationType type) {
        MessageAutomation a = automationRepository.findByType(type)
                .orElseThrow(() -> new BusinessRuleException("احفظ الرسالة الأساسية أولاً"));
        MessageVariant baseRow = variantRepository.findByAutomationIdOrderBySortOrder(a.getId()).stream()
                .filter(v -> v.getSortOrder() == 0).findFirst().orElse(null);
        if (baseRow == null || baseRow.getBody() == null || baseRow.getBody().isBlank()) {
            throw new BusinessRuleException("اكتب الرسالة الأساسية أولاً قبل توليد الصيغ البديلة");
        }

        List<String> generated = aiVariantClient.generate(baseRow.getBody(), ALTERNATIVES);
        variantRepository.deleteByAutomationIdAndSortOrderGreaterThan(a.getId(), 0);
        List<MessageVariant> fresh = new ArrayList<>();
        int order = 1;
        for (String body : generated) {
            MessageVariant v = newVariant(a.getId(), body, order++);
            // A generated wording starts with the base message's own image setting.
            v.setSendAsImage(baseRow.isSendAsImage());
            fresh.add(v);
        }
        variantRepository.saveAll(fresh);
        return toResponse(a);
    }

    @Transactional
    public VariantResponse updateVariant(UUID id, VariantUpdateRequest req) {
        MessageVariant v = variantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("الصيغة غير موجودة"));
        v.setBody(req.body().strip());
        v.setSendAsImage(Boolean.TRUE.equals(req.sendAsImage()));
        variantRepository.save(v);
        return new VariantResponse(v.getId(), v.getBody(), v.isSendAsImage());
    }

    private MessageAutomation ensure(AutomationType type) {
        return automationRepository.findByType(type).orElseGet(() -> {
            MessageAutomation a = new MessageAutomation();
            a.setType(type);
            a.setEnabled(true);
            a.setAudience(MessageAudience.PARENT);
            return automationRepository.save(a);
        });
    }

    private static MessageVariant newVariant(UUID automationId, String body, int sortOrder) {
        MessageVariant v = new MessageVariant();
        v.setAutomationId(automationId);
        v.setBody(body);
        v.setSortOrder(sortOrder);
        return v;
    }

    private AutomationResponse toResponse(MessageAutomation a) {
        List<MessageVariant> variants = variantRepository.findByAutomationIdOrderBySortOrder(a.getId());
        String base = "";
        boolean baseSendAsImage = false;
        List<VariantResponse> alternatives = new ArrayList<>();
        for (MessageVariant v : variants) {
            if (v.getSortOrder() == 0) {
                base = v.getBody();
                baseSendAsImage = v.isSendAsImage();
            } else {
                alternatives.add(new VariantResponse(v.getId(), v.getBody(), v.isSendAsImage()));
            }
        }
        return new AutomationResponse(a.getType(), a.isEnabled(), a.getAudience(),
                a.getWeekStartDay(), a.getWeekEndDay(), base, baseSendAsImage, alternatives);
    }

    // ── Per-lesson attendance / absence (Lessons page buttons) ────────────

    /** Who has already been messaged about this lesson, per kind - the roster's
     *  "sent / not yet" columns and what its send buttons aim at. */
    @Transactional(readOnly = true)
    public LectureMessageStatus messageStatus(UUID lectureId) {
        return new LectureMessageStatus(
                new ArrayList<>(logRepository.sentStudentIds(lectureId, "ATTENDANCE")),
                new ArrayList<>(logRepository.sentStudentIds(lectureId, "EXAM_GRADE")));
    }

    /**
     * The group's students who missed this lesson, each marked with whether the
     * absence message already reached them.
     *
     * <p>"Missed" means present in NO group of this lesson: a student who sat it
     * with another group attended it, and telling their parent otherwise would be
     * a lie the system had all the information to avoid.
     */
    @Transactional(readOnly = true)
    public List<LectureAbsentee> absentees(UUID lectureId, UUID groupId) {
        Set<UUID> present = registrationRepository.presentStudentIds(lectureId, RegistrationStatus.PRESENT);
        Set<UUID> sent = logRepository.sentStudentIds(lectureId, "ABSENCE");
        List<LectureAbsentee> out = new ArrayList<>();
        for (Student s : studentRepository.findByGroup_IdAndActiveTrue(groupId)) {
            if (present.contains(s.getId())) {
                continue;
            }
            out.add(new LectureAbsentee(s.getId(), s.getSerial(), s.getName(),
                    phones(s.getParentPhones()), sent.contains(s.getId())));
        }
        out.sort(java.util.Comparator.comparing(a -> a.serial() == null ? Integer.MAX_VALUE : a.serial()));
        return out;
    }

    /** A phone array as a clean list - a stored array may hold nulls or blanks. */
    private static List<String> phones(String[] raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String p : raw) {
            if (p != null && !p.isBlank()) {
                out.add(p);
            }
        }
        return out;
    }

    /** Present-not-yet-messaged and absent-not-yet-messaged counts for one lesson-group. */
    @Transactional(readOnly = true)
    public LecturePendingCounts pendingCounts(UUID lectureId, UUID groupId) {
        Set<UUID> attendanceSent = logRepository.sentStudentIds(lectureId, "ATTENDANCE");
        int attendance = 0;
        for (Registration r : registrationRepository.findByLectureIdAndGroupIdAndStatus(
                lectureId, groupId, RegistrationStatus.PRESENT)) {
            if (!attendanceSent.contains(r.getStudent().getId())) {
                attendance++;
            }
        }

        Set<UUID> present = registrationRepository.presentStudentIds(lectureId, RegistrationStatus.PRESENT);
        Set<UUID> absenceSent = logRepository.sentStudentIds(lectureId, "ABSENCE");
        int absence = 0;
        for (Student s : studentRepository.findByGroup_IdAndActiveTrue(groupId)) {
            if (!present.contains(s.getId()) && !absenceSent.contains(s.getId())) {
                absence++;
            }
        }
        return new LecturePendingCounts(attendance, absence);
    }

    /**
     * Send the attendance message to present students not yet messaged for this lesson.
     *
     * <p>Deliberately NOT {@code @Transactional}. It used to be, and that made a
     * routine broadcast the most expensive thing the server did: a group of forty
     * students meant forty sequential Green API round trips with a Hikari
     * connection pinned open for the whole run. The pool holds eight, so eight
     * teachers pressing "send" at the same moment starved every other request in
     * the application - a login, a search, a health check - until the sends
     * finished or the 30-second connection timeout fired.
     *
     * <p>Now the work splits in three: read what to send (one short transaction),
     * send it (no transaction, no connection held), then record each attempt
     * (its own short transaction per row). The recorded outcome is also strictly
     * more truthful than before, because a failure late in the batch can no
     * longer roll back the log of messages that genuinely were delivered.
     */
    public WhatsappSendResult sendLectureAttendance(UUID lectureId, UUID groupId, UUID byUser, String byName) {
        return deliver(self.planLectureAttendance(lectureId, groupId), byUser, byName, lectureId, groupId);
    }

    @Transactional(readOnly = true)
    public List<PlannedMessage> planLectureAttendance(UUID lectureId, UUID groupId) {
        MessageAutomation a = requireAutomation(AutomationType.ATTENDANCE);
        List<MessageVariant> variants = requireVariants(a);
        Lecture lecture = lectureRepository.findById(lectureId).orElse(null);
        Group group = groupRepository.findById(groupId).orElse(null);
        String teacher = teacherName();
        Set<UUID> already = logRepository.sentStudentIds(lectureId, "ATTENDANCE");

        List<PlannedMessage> planned = new ArrayList<>();
        for (Registration r : registrationRepository.findByLectureIdAndGroupIdAndStatus(
                lectureId, groupId, RegistrationStatus.PRESENT)) {
            Student s = r.getStudent();
            if (already.contains(s.getId())) {
                continue;
            }
            planned.add(plan(variants, s, group, lecture, r, "حاضر", "ATTENDANCE",
                    a.getAudience(), teacher));
        }
        return planned;
    }

    /** Send the absence message to the group's students who missed this lesson. */
    public WhatsappSendResult sendLectureAbsence(UUID lectureId, UUID groupId, UUID byUser, String byName) {
        return deliver(self.planLectureAbsence(lectureId, groupId), byUser, byName, lectureId, groupId);
    }

    @Transactional(readOnly = true)
    public List<PlannedMessage> planLectureAbsence(UUID lectureId, UUID groupId) {
        MessageAutomation a = requireAutomation(AutomationType.ABSENCE);
        List<MessageVariant> variants = requireVariants(a);
        Lecture lecture = lectureRepository.findById(lectureId).orElse(null);
        Group group = groupRepository.findById(groupId).orElse(null);
        String teacher = teacherName();
        Set<UUID> present = registrationRepository.presentStudentIds(lectureId, RegistrationStatus.PRESENT);
        Set<UUID> already = logRepository.sentStudentIds(lectureId, "ABSENCE");

        List<PlannedMessage> planned = new ArrayList<>();
        for (Student s : studentRepository.findByGroup_IdAndActiveTrue(groupId)) {
            if (present.contains(s.getId()) || already.contains(s.getId())) {
                continue;
            }
            // An absent student has no registration row, so there is no attendance
            // instant to quote - the attendance-time variables stay blank.
            planned.add(plan(variants, s, group, lecture, null, "غائب", "ABSENCE",
                    a.getAudience(), teacher));
        }
        return planned;
    }

    /**
     * The send phase: one HTTP call per recipient, outside any transaction. A
     * student counts as reached when at least one of their recipients accepted,
     * which is the count the two methods above have always reported.
     */
    private WhatsappSendResult deliver(List<PlannedMessage> planned, UUID byUser, String byName,
            UUID lectureId, UUID groupId) {
        int sent = 0;
        for (PlannedMessage m : planned) {
            boolean any = false;
            for (WhatsappLogSender.Recipient r : m.recipients()) {
                any |= logSender.logAndSend(r, m.body(), "MANUAL", m.origin(),
                        lectureId, groupId, byUser, byName, m.asImage());
            }
            if (any) {
                sent++;
            }
        }
        return new WhatsappSendResult(sent, planned.size() - sent, planned.size());
    }

    /**
     * Called after a registration commits: auto-send attendance if this pair is
     * opted in.
     *
     * <p>This is the highest-frequency send in the product - it can fire on every
     * single registration at the desk - so it follows the same split as the
     * broadcasts: decide and render inside a transaction, then send outside it.
     * The caller no longer wraps this in a transaction of its own; the planning
     * half opens one, which is also what binds the tenant for Hibernate.
     */
    public void sendAttendanceOnRegister(UUID studentId, UUID groupId, UUID lectureId) {
        PlannedMessage m = self.planAttendanceOnRegister(studentId, groupId, lectureId);
        if (m == null) {
            return;
        }
        for (WhatsappLogSender.Recipient r : m.recipients()) {
            logSender.logAndSend(r, m.body(), "SYSTEM", m.origin(), lectureId, groupId,
                    null, null, m.asImage());
        }
    }

    /** The decision and the rendering. Null means "nothing to send". */
    @Transactional(readOnly = true)
    public PlannedMessage planAttendanceOnRegister(UUID studentId, UUID groupId, UUID lectureId) {
        if (studentId == null || groupId == null || lectureId == null) {
            return null;
        }
        boolean enabled = optinRepository.findByLectureIdAndGroupId(lectureId, groupId)
                .map(AttendanceAutoOptin::isEnabled).orElse(false);
        if (!enabled) {
            return null;
        }
        // A student already messaged for this lesson must not be messaged again.
        if (logRepository.existsByStudentIdAndLectureIdAndOriginAndStatus(
                studentId, lectureId, "ATTENDANCE", "SENT")) {
            return null;
        }
        MessageAutomation a = automationRepository.findByType(AutomationType.ATTENDANCE).orElse(null);
        if (a == null) {
            return null;
        }
        List<MessageVariant> variants = variantRepository.findByAutomationIdOrderBySortOrder(a.getId());
        if (variants.isEmpty()) {
            return null;
        }
        Student s = studentRepository.findById(studentId).orElse(null);
        if (s == null) {
            return null;
        }
        Group group = groupRepository.findById(groupId).orElse(null);
        Lecture lecture = lectureRepository.findById(lectureId).orElse(null);
        Registration reg = registrationRepository
                .findByLectureIdAndStudentIdAndGroupId(lectureId, studentId, groupId).orElse(null);
        return plan(variants, s, group, lecture, reg, "حاضر", "ATTENDANCE",
                a.getAudience(), teacherName());
    }

    /**
     * One student's message, fully resolved: every entity read and every
     * placeholder substituted, so nothing here needs a database session again.
     * That is what lets the send phase run with no connection held.
     */
    record PlannedMessage(List<WhatsappLogSender.Recipient> recipients, String body,
            String origin, boolean asImage) {
    }

    /**
     * Renders a random variant for one student and names every recipient the
     * template targets (parent and/or student). Reads only - the send happens in
     * {@link #deliver}, after the transaction that called this has closed.
     */
    private PlannedMessage plan(List<MessageVariant> variants, Student s,
            Group group, Lecture lecture, Registration registration, String status, String origin,
            MessageAudience audience, String teacher) {
        Map<String, String> vars = MessageText.studentVars(s, teacher);
        // The group the lesson was attended under wins over the student's own.
        MessageText.putGroup(vars, group == null ? s.getGroup() : group);
        MessageText.putLesson(vars, lecture);
        MessageText.putAttendance(vars, registration, status);
        MessageText.putExam(vars, registration, lecture);
        vars.put("parent.name", parentName(s.getId()));
        MessageVariant picked = randomVariant(variants);
        String code = s.getSerial() == null ? "" : String.valueOf(s.getSerial());
        // Each wording carries its own image flag; honour the one that was drawn.
        return new PlannedMessage(recipients(s, code, audience),
                MessageText.render(picked.getBody(), vars), origin, picked.isSendAsImage());
    }

    /** Renders and sends in one step. Used by the single-student automatic path. */
    private boolean dispatch(List<MessageVariant> variants, Student s,
            Group group, Lecture lecture, Registration registration, String status,
            String source, String origin,
            MessageAudience audience, UUID lectureId, UUID groupId, UUID byUser, String byName,
            String teacher) {
        PlannedMessage m = plan(variants, s, group, lecture, registration, status, origin,
                audience, teacher);
        boolean any = false;
        for (WhatsappLogSender.Recipient r : m.recipients()) {
            any |= logSender.logAndSend(r, m.body(), source, origin, lectureId, groupId,
                    byUser, byName, m.asImage());
        }
        return any;
    }

    /** The recipients a template targets: parent, student, or both. */
    private static List<WhatsappLogSender.Recipient> recipients(Student s, String code, MessageAudience audience) {
        List<WhatsappLogSender.Recipient> list = new ArrayList<>(2);
        MessageAudience aud = audience == null ? MessageAudience.PARENT : audience;
        if (aud == MessageAudience.PARENT || aud == MessageAudience.BOTH) {
            list.add(new WhatsappLogSender.Recipient(
                    s.getName(), MessageText.firstPhone(s.getParentPhones()), code, "PARENT", s.getId()));
        }
        if (aud == MessageAudience.STUDENT || aud == MessageAudience.BOTH) {
            list.add(new WhatsappLogSender.Recipient(
                    s.getName(), MessageText.firstPhone(s.getStudentPhones()), code, "STUDENT", s.getId()));
        }
        return list;
    }

    /**
     * Called once after a student is created: sends the "new student" message
     * with the barcode card attached, to whichever recipients the template
     * targets.
     *
     * <p>Deliberately NOT {@code @Transactional} any more. It used to be, and
     * that transaction covered the two slowest things the process does: the
     * openhtmltopdf render of the barcode card (CPU-bound, with a font to embed)
     * and the multipart upload of that PDF to Green API, once per recipient. A
     * pooled connection was held across both, on the async pool, once per
     * student created - so a bulk intake of new students could occupy several
     * connections at a time doing no database work whatsoever.
     */
    public void sendNewStudent(UUID studentId) {
        PlannedCard planned = self.planNewStudent(studentId);
        if (planned == null) {
            return;
        }
        // The barcode card travels with the welcome text so the parent has the
        // code to hand. Rendered HERE, outside the read transaction above: this
        // is an openhtmltopdf run with a 434 KB font to embed, and it has no
        // business holding a database connection. A failed render must not
        // swallow the message either - fall back to plain text.
        byte[] card;
        String fileName;
        try {
            card = barcodeService.renderPdf(planned.studentId());
            fileName = barcodeService.fileName(planned.studentId());
        } catch (RuntimeException ex) {
            card = null;
            fileName = null;
        }
        for (WhatsappLogSender.Recipient r : planned.recipients()) {
            logSender.logAndSendFile(r, planned.body(), card, fileName, "SYSTEM", "NEW_STUDENT");
        }
    }

    /** The welcome message and who gets it, resolved but not yet rendered or sent. */
    record PlannedCard(List<WhatsappLogSender.Recipient> recipients, String body, UUID studentId) {
    }

    @Transactional(readOnly = true)
    public PlannedCard planNewStudent(UUID studentId) {
        if (studentId == null) {
            return null;
        }
        MessageAutomation a = automationRepository.findByType(AutomationType.NEW_STUDENT).orElse(null);
        if (a == null || !a.isEnabled()) {
            return null;
        }
        List<MessageVariant> variants = variantRepository.findByAutomationIdOrderBySortOrder(a.getId());
        if (variants.isEmpty() || variants.stream().allMatch(v -> v.getBody() == null || v.getBody().isBlank())) {
            return null;
        }
        // Once per student, ever - a later edit must not re-send the welcome.
        if (logRepository.existsByStudentIdAndOriginAndStatus(studentId, "NEW_STUDENT", "SENT")) {
            return null;
        }
        Student s = studentRepository.findById(studentId).orElse(null);
        if (s == null) {
            return null;
        }
        Map<String, String> vars = MessageText.studentVars(s, teacherName());
        vars.put("parent.name", parentName(s.getId()));
        MessageVariant picked = randomVariant(variants);
        String body = MessageText.render(picked.getBody(), vars);
        String code = s.getSerial() == null ? "" : String.valueOf(s.getSerial());
        return new PlannedCard(recipients(s, code, a.getAudience()), body, studentId);
    }

    /**
     * Re-send the "new student" message + barcode card for an existing student, on
     * the teacher's say-so (the barcode button). Uses the SAME NEW_STUDENT template
     * and recipients the automatic welcome uses - so the manual button and the
     * on-create message are one and the same - but skips the automatic path's
     * enabled/once-ever guards, since this is an explicit resend.
     */
    public String sendBarcode(UUID studentId) {
        PlannedCard planned = self.planStudentCard(studentId, AutomationType.NEW_STUDENT);
        byte[] card;
        String fileName;
        try {
            card = barcodeService.renderPdf(studentId);
            fileName = barcodeService.fileName(studentId);
        } catch (RuntimeException ex) {
            card = null;
            fileName = null;
        }
        String phone = null;
        for (WhatsappLogSender.Recipient r : planned.recipients()) {
            logSender.logAndSendFile(r, planned.body(), card, fileName, "MANUAL", "BARCODE");
            if (phone == null) {
                phone = r.phone();
            }
        }
        return phone;
    }

    /**
     * The message body + recipients for a manual student-card send, resolved from
     * one automation template. Requires the template to be written (throws the same
     * "write the message first" error the other manual sends do).
     */
    @Transactional(readOnly = true)
    public PlannedCard planStudentCard(UUID studentId, AutomationType type) {
        Student s = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("الطالب غير موجود"));
        MessageAutomation a = requireAutomation(type);
        List<MessageVariant> variants = requireVariants(a);
        Map<String, String> vars = MessageText.studentVars(s, teacherName());
        vars.put("parent.name", parentName(s.getId()));
        MessageVariant picked = randomVariant(variants);
        String body = MessageText.render(picked.getBody(), vars);
        String code = s.getSerial() == null ? "" : String.valueOf(s.getSerial());
        return new PlannedCard(recipients(s, code, a.getAudience()), body, studentId);
    }

    /**
     * Send the student report PDF with the REPORT template's text, to the one
     * recipient the button chose (the parent or the student). Unlike the other
     * templates the recipient is NOT the template's audience here - the report has
     * an explicit parent/student button - so the template supplies only the text.
     */
    public String sendReport(UUID studentId, boolean toParent) {
        PlannedCard planned = self.planReportCard(studentId, toParent);
        byte[] pdf;
        String fileName;
        try {
            pdf = reportService.renderPdf(studentId);
            fileName = reportService.fileName(studentId);
        } catch (RuntimeException ex) {
            pdf = null;
            fileName = null;
        }
        WhatsappLogSender.Recipient r = planned.recipients().get(0);
        logSender.logAndSendFile(r, planned.body(), pdf, fileName, "MANUAL", "REPORT");
        return r.phone();
    }

    @Transactional(readOnly = true)
    public PlannedCard planReportCard(UUID studentId, boolean toParent) {
        Student s = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("الطالب غير موجود"));
        MessageAutomation a = requireAutomation(AutomationType.REPORT);
        List<MessageVariant> variants = requireVariants(a);
        String phone = toParent
                ? MessageText.firstPhone(s.getParentPhones())
                : MessageText.firstPhone(s.getStudentPhones());
        if (phone == null || phone.isBlank()) {
            throw new BusinessRuleException(toParent
                    ? "لا يوجد رقم هاتف لولي الأمر" : "لا يوجد رقم هاتف للطالب");
        }
        Map<String, String> vars = MessageText.studentVars(s, teacherName());
        vars.put("parent.name", parentName(s.getId()));
        MessageVariant picked = randomVariant(variants);
        String body = MessageText.render(picked.getBody(), vars);
        String code = s.getSerial() == null ? "" : String.valueOf(s.getSerial());
        WhatsappLogSender.Recipient r = new WhatsappLogSender.Recipient(
                s.getName(), phone, code, toParent ? "PARENT" : "STUDENT", s.getId());
        return new PlannedCard(java.util.List.of(r), body, studentId);
    }

    /**
     * Send the exam-grade message to this lesson-group's graded students who have
     * not been told yet.
     *
     * <p>Grades used to go out on their own, one message per mark the moment it
     * was typed. They no longer do: a mark is often corrected seconds after it is
     * entered, and a message already read cannot be corrected at all. The teacher
     * now presses send when the column is finished, which is also the only point
     * at which "the grades are ready" is a fact rather than a guess.
     */
    public WhatsappSendResult sendLectureExamGrade(UUID lectureId, UUID groupId, UUID byUser, String byName) {
        return deliver(self.planLectureExamGrade(lectureId, groupId), byUser, byName, lectureId, groupId);
    }

    @Transactional(readOnly = true)
    public List<PlannedMessage> planLectureExamGrade(UUID lectureId, UUID groupId) {
        MessageAutomation a = requireAutomation(AutomationType.EXAM_GRADE);
        List<MessageVariant> variants = requireVariants(a);
        Lecture lecture = lectureRepository.findById(lectureId).orElse(null);
        Group group = groupRepository.findById(groupId).orElse(null);
        String teacher = teacherName();
        Set<UUID> already = logRepository.sentStudentIds(lectureId, "EXAM_GRADE");

        List<PlannedMessage> planned = new ArrayList<>();
        for (Registration r : registrationRepository.findByLectureIdAndGroupIdAndStatus(
                lectureId, groupId, RegistrationStatus.PRESENT)) {
            // No mark, nothing to report - and a student already told stays told.
            if (r.getExamScore() == null || already.contains(r.getStudent().getId())) {
                continue;
            }
            planned.add(plan(variants, r.getStudent(), group, lecture, r, "حاضر", "EXAM_GRADE",
                    a.getAudience(), teacher));
        }
        return planned;
    }

    private MessageAutomation requireAutomation(AutomationType type) {
        return automationRepository.findByType(type).orElseThrow(() -> new BusinessRuleException(
                "اكتب نص رسالة " + automationLabel(type) + " أولاً من صفحة الرسائل"));
    }

    private static String automationLabel(AutomationType type) {
        return switch (type) {
            case ABSENCE -> "الغياب";
            case EXAM_GRADE -> "درجة الاختبار";
            case NEW_STUDENT -> "طالب جديد";
            case REPORT -> "التقرير";
            default -> "الحضور";
        };
    }

    private List<MessageVariant> requireVariants(MessageAutomation a) {
        List<MessageVariant> variants = variantRepository.findByAutomationIdOrderBySortOrder(a.getId());
        boolean empty = variants.isEmpty()
                || variants.stream().allMatch(v -> v.getBody() == null || v.getBody().isBlank());
        if (empty) {
            throw new BusinessRuleException("اكتب نص الرسالة أولاً من صفحة الرسائل");
        }
        return variants;
    }

    private static MessageVariant randomVariant(List<MessageVariant> variants) {
        return variants.get(ThreadLocalRandom.current().nextInt(variants.size()));
    }

    // ── Attendance auto-send opt-in (registration page toggle) ────────────

    @Transactional(readOnly = true)
    public AttendanceOptinResponse optin(UUID lectureId, UUID groupId) {
        boolean enabled = optinRepository.findByLectureIdAndGroupId(lectureId, groupId)
                .map(AttendanceAutoOptin::isEnabled).orElse(false);
        return new AttendanceOptinResponse(enabled);
    }

    @Transactional
    public AttendanceOptinResponse setOptin(UUID lectureId, UUID groupId, boolean enabled) {
        AttendanceAutoOptin row = optinRepository.findByLectureIdAndGroupId(lectureId, groupId)
                .orElseGet(() -> {
                    AttendanceAutoOptin fresh = new AttendanceAutoOptin();
                    fresh.setLectureId(lectureId);
                    fresh.setGroupId(groupId);
                    return fresh;
                });
        row.setEnabled(enabled);
        optinRepository.save(row);
        return new AttendanceOptinResponse(enabled);
    }

    /**
     * Whether a student's parent is reachable on WhatsApp - checked before a
     * toggle-on attendance is committed, so an unreachable number is caught up
     * front rather than silently attended. UNKNOWN (Green API not configured or
     * the call failed) never blocks: the caller attends and shows the reason.
     */
    // Not transactional: the only database work is one lookup by id, which opens
    // its own short transaction. Wrapping the Green API round trip that follows
    // would hold a pooled connection open for the length of someone else's
    // network, and this is called from the registration desk on every toggle.
    public AttendanceWhatsappCheck parentWhatsappStatus(UUID studentId) {
        Student s = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("الطالب غير موجود"));
        String phone = MessageText.firstPhone(s.getParentPhones());
        if (phone == null || phone.isBlank()) {
            return new AttendanceWhatsappCheck("NO_PHONE", "لا يوجد رقم مسجّل لولي الأمر");
        }
        GreenApiClient.WhatsappCheck check = greenApiClient.checkWhatsapp(phone);
        if (!check.checked()) {
            return new AttendanceWhatsappCheck("UNKNOWN", "تعذّر التحقق من واتساب");
        }
        return new AttendanceWhatsappCheck(check.existsWhatsapp() ? "ON" : "OFF", null);
    }

    // ── Manual send ───────────────────────────────────────────────────────

    /** One resolved recipient with its already-rendered message body. */
    record Pending(WhatsappLogSender.Recipient recipient, String body) {}

    /**
     * The manual broadcast. Reads its recipient list in one short transaction and
     * then sends outside it, for the reason spelled out on
     * {@link #sendLectureAttendance} - this is the largest burst the system
     * produces (it can target the entire roster), so it is the one that must not
     * hold a database connection while it talks to WhatsApp.
     */
    public WhatsappSendResult send(WhatsappSendRequest req, UUID sentByUserId, String sentByName) {
        List<Pending> pending = self.planManualSend(req);
        int sent = 0;
        for (Pending item : pending) {
            if (logSender.logAndSend(item.recipient(), item.body(), "MANUAL", "MANUAL",
                    null, null, sentByUserId, sentByName, false)) {
                sent++;
            }
        }
        return new WhatsappSendResult(sent, pending.size() - sent, pending.size());
    }

    @Transactional(readOnly = true)
    public List<Pending> planManualSend(WhatsappSendRequest req) {
        adminId();
        String teacher = teacherName();
        MessageAudience audience = req.audience() == null ? MessageAudience.STUDENT : req.audience();

        List<Pending> pending = new ArrayList<>();
        Set<String> seenPhones = new HashSet<>();

        for (Student s : resolveStudents(req)) {
            Map<String, String> vars = MessageText.studentVars(s, teacher);
            vars.put("parent.name", parentName(s.getId()));
            String body = MessageText.render(req.body(), vars);
            String code = s.getSerial() == null ? "" : String.valueOf(s.getSerial());
            if (audience == MessageAudience.STUDENT || audience == MessageAudience.BOTH) {
                add(pending, seenPhones, new WhatsappLogSender.Recipient(
                        s.getName(), MessageText.firstPhone(s.getStudentPhones()), code, "STUDENT", s.getId()), body);
            }
            if (audience == MessageAudience.PARENT || audience == MessageAudience.BOTH) {
                add(pending, seenPhones, new WhatsappLogSender.Recipient(
                        s.getName(), MessageText.firstPhone(s.getParentPhones()), code, "PARENT", s.getId()), body);
            }
        }

        if (req.parentIds() != null && !req.parentIds().isEmpty()) {
            for (Parent p : parentRepository.findAllById(req.parentIds())) {
                Map<String, String> vars = MessageText.baseVars();
                vars.putAll(MessageText.globals(teacher));
                vars.put("parent.name", p.getName() == null ? "" : p.getName());
                vars.put("parent.phone", p.getPhone() == null ? "" : p.getPhone());
                String body = MessageText.render(req.body(), vars);
                String code = p.getSerial() == null ? "" : String.valueOf(p.getSerial());
                add(pending, seenPhones, new WhatsappLogSender.Recipient(
                        p.getName(), p.getPhone(), code, "PARENT", null), body);
            }
        }

        if (pending.isEmpty()) {
            throw new BusinessRuleException("لا يوجد مستلمون مطابقون للتحديد");
        }
        return pending;
    }

    /** Adds a recipient, skipping a duplicate phone (a blank phone still logs a failure). */
    private static void add(List<Pending> out, Set<String> seenPhones,
            WhatsappLogSender.Recipient r, String body) {
        String phone = r.phone();
        if (phone == null || phone.isBlank() || seenPhones.add(phone)) {
            out.add(new Pending(r, body));
        }
    }

    /** Union of every student facet, scoped to the workspace by @TenantId. */
    private Set<Student> resolveStudents(WhatsappSendRequest req) {
        Set<Student> set = new LinkedHashSet<>();
        if (req.grades() != null) {
            for (String g : req.grades()) {
                set.addAll(byFilter(f -> f.grade(g)));
            }
        }
        if (req.groupIds() != null) {
            for (UUID gid : req.groupIds()) {
                set.addAll(byFilter(f -> f.groupId(gid)));
            }
        }
        if (req.genders() != null) {
            for (Gender g : req.genders()) {
                set.addAll(byFilter(f -> f.gender(g)));
            }
        }
        if (req.religions() != null) {
            for (Religion r : req.religions()) {
                set.addAll(byFilter(f -> f.religion(r)));
            }
        }
        if (req.academicTrack() != null) {
            set.addAll(byFilter(f -> f.academicTrack(req.academicTrack())));
        }
        if (req.studentIds() != null && !req.studentIds().isEmpty()) {
            set.addAll(studentRepository.findAllById(req.studentIds()));
        }
        return set;
    }

    private List<Student> byFilter(UnaryOperator<FilterBuilder> b) {
        return studentRepository.findAll(StudentSpecifications.matching(b.apply(new FilterBuilder()).build()));
    }

    /** Single-facet StudentFilter builder (mirrors the composer's own). */
    private static final class FilterBuilder {
        private String grade;
        private UUID groupId;
        private Gender gender;
        private AcademicTrack academicTrack;
        private Religion religion;

        FilterBuilder grade(String v) { this.grade = v; return this; }
        FilterBuilder groupId(UUID v) { this.groupId = v; return this; }
        FilterBuilder gender(Gender v) { this.gender = v; return this; }
        FilterBuilder academicTrack(AcademicTrack v) { this.academicTrack = v; return this; }
        FilterBuilder religion(Religion v) { this.religion = v; return this; }

        StudentFilter build() {
            return new StudentFilter(null, null, null, null, grade, groupId, gender, academicTrack,
                    null, religion, null);
        }
    }

    /**
     * The workspace owner - the teacher the student belongs to, NOT whoever is
     * signed in. An assistant sending on the teacher's behalf must produce a
     * message (and a barcode, a report, an invoice) carrying the teacher's name;
     * the tenant is exactly that, for an admin and an assistant alike.
     */
    private String teacherName() {
        return userRepository.findById(adminId())
                .map(u -> u.getUsername())
                .filter(n -> n != null && !n.isBlank())
                .orElseGet(settingsService::senderName);
    }

    /**
     * The name of a student's approved guardian account, when one exists. A
     * student record holds the guardian's NUMBER but never their name, so this is
     * the only place the {parent.name} variable can be filled from - and it stays
     * blank rather than guessing when no parent has linked themselves.
     */
    private String parentName(UUID studentId) {
        return parentLinkRepository.findByStudentIdAndStatus(studentId, LinkStatus.APPROVED).stream()
                .findFirst()
                .flatMap(link -> parentRepository.findById(link.getParentId()))
                .map(Parent::getName)
                .filter(n -> n != null && !n.isBlank())
                .orElse("");
    }

    // ── History ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<WhatsappMessageLogResponse> log(Pageable pageable) {
        return logRepository.findAllByOrderByCreatedAtDesc(pageable).map(WhatsappMessagingService::toLogResponse);
    }

    private static WhatsappMessageLogResponse toLogResponse(WhatsappMessageLog m) {
        return new WhatsappMessageLogResponse(m.getId(), m.getRecipientName(), m.getPhone(),
                m.getRecipientCode(), m.getRecipientType(), m.getBody(), m.getStatus(),
                m.getFailureReason(), m.getSource(), m.getOrigin(), m.getSentByName(), m.getCreatedAt());
    }
}
