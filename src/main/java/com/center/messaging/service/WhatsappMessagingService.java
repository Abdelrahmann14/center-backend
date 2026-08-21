package com.center.messaging.service;

import java.time.LocalDate;
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
import org.springframework.transaction.annotation.Propagation;
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
import com.center.messaging.dto.LectureAbsentee;
import com.center.messaging.dto.LectureMessageStatus;
import com.center.messaging.dto.LecturePendingCounts;
import com.center.messaging.dto.WhatsappMessageLogResponse;
import com.center.messaging.dto.WhatsappSendResult;
import com.center.messaging.entity.AttendanceAutoOptin;
import com.center.messaging.entity.MessageAutomation;
import com.center.messaging.entity.MessageVariant;
import com.center.messaging.entity.WhatsappMessageLog;
import com.center.messaging.repository.AttendanceAutoOptinRepository;
import com.center.messaging.repository.MessageAutomationRepository;
import com.center.messaging.repository.MessageVariantRepository;
import com.center.messaging.repository.WhatsappMessageLogRepository;
import com.center.registration.entity.Registration;
import com.center.registration.repository.RegistrationRepository;
import com.center.settings.service.SettingsService;
import com.center.student.dto.StudentFilter;
import com.center.student.entity.Student;
import com.center.student.repository.StudentRepository;
import com.center.student.specification.StudentSpecifications;
import com.center.user.repository.UserRepository;
import com.center.whatsapp.dto.WhatsappResponsibilityResponse;
import com.center.whatsapp.entity.WhatsappInstance;
import com.center.whatsapp.service.WhatsappAvailabilityService;
import com.center.whatsapp.service.WhatsappInstanceService;
import com.center.whatsapp.service.WhatsappResponsibilityCatalog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The Messages page's server side: the automated-message config (attendance /
 * absence), AI variant generation, the manual WhatsApp send, the per-lesson
 * attendance/absence sends triggered from the Lessons page, and the send log.
 * All WhatsApp only - nothing here touches the in-app notification inbox.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WhatsappMessagingService {

    /**
     * Who the barcode card goes to, on both paths that carry it - the welcome
     * fired when a student is created, and the resend button.
     *
     * <p>Fixed rather than configurable. The card is the student's own identity
     * card: the code printed on it is what the desk scans to register them, so
     * the phone it has to live on is theirs. Sending it to a guardian instead
     * puts it where it cannot be used, and NEW_STUDENT is the only automation
     * whose message is a document rather than a notice - the rest are news about
     * a student, which a parent is entitled to and the setting still governs.
     */
    private static final MessageAudience BARCODE_AUDIENCE = MessageAudience.STUDENT;

    private final MessageAutomationRepository automationRepository;
    private final MessageVariantRepository variantRepository;
    private final WhatsappMessageLogRepository logRepository;
    private final AttendanceAutoOptinRepository optinRepository;
    private final StudentRepository studentRepository;
    private final RegistrationRepository registrationRepository;
    private final GroupRepository groupRepository;
    private final LectureRepository lectureRepository;
    private final UserRepository userRepository;
    private final SettingsService settingsService;
    private final WhatsappLogSender logSender;
    private final WhatsappInstanceService instances;
    private final WhatsappAvailabilityService availability;
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

    /**
     * The recipient a type is created with. Everything is news for the guardian
     * except the barcode card, which is the student's own.
     */
    private static MessageAudience audienceFor(AutomationType type) {
        return type == AutomationType.NEW_STUDENT ? BARCODE_AUDIENCE : MessageAudience.PARENT;
    }

    /**
     * The row one message type hangs its wording off, and a wording to hang -
     * created on first use if the workspace has neither.
     *
     * <p>These used to appear when somebody opened the automated-messages page.
     * That page is gone: what leaves is an approved Meta template now, so there
     * was nothing left on it to decide. Provisioning therefore moved to the
     * first send, which is the only moment left that needs the row to exist.
     *
     * <p>In a transaction of its own because every caller is reading inside a
     * read-only one, and through {@code self} so the proxy - and therefore the
     * annotation - is actually involved.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MessageAutomation provision(AutomationType type) {
        MessageAutomation a = automationRepository.findByType(type).orElseGet(() -> {
            MessageAutomation fresh = new MessageAutomation();
            fresh.setType(type);
            // Every automation is enabled: this flag says the type exists and is
            // configured, not that anybody wants it to fire. Whether the
            // new-student welcome actually goes out is decided per ACCOUNT, on
            // users.barcode_auto_send - see barcodeAutoSend().
            fresh.setEnabled(true);
            fresh.setAudience(audienceFor(type));
            return automationRepository.save(fresh);
        });
        if (bodies(a).isEmpty()) {
            variantRepository.save(newVariant(a.getId(), defaultBody(type), 0));
        }
        return a;
    }

    /**
     * What the history records for a type before anyone has said otherwise.
     *
     * <p>Deliberately a copy of the approved template's own wording: the text
     * stored here is NOT what WhatsApp delivers - the template is - it is what
     * the teacher reads back in the log. A history that paraphrases the message
     * a parent actually got is worse than no history, so these track the
     * templates and are the thing to update when a template's wording changes.
     */
    private static String defaultBody(AutomationType type) {
        return switch (type) {
            case ABSENCE -> """
                    السيد ولي أمر الطالب : *{student.name}*
                    نحيط سيادتكم علماً بأن الطالب تغيّب عن حصة *{lesson.name}* بتاريخ *{absence.date}*.

                    لأي استفسار يمكنكم مراسلتنا على رقم المكتب *{teacher.office_phone}*.
                    شكرا""";
            case NEW_STUDENT -> """
                    تم تسجيلك في مكتب *{teacher.name}*.
                    مرفق مع الرسالة بطاقة الباركود الخاصة بيك، الكود بتاعك هو *{student.serial}*.

                    لأي استفسار يمكنكم مراسلتنا على رقم المكتب *{teacher.office_phone}*.
                    شكرا""";
            case EXAM_GRADE -> """
                    السيد ولي أمر الطالب : *{student.name}*
                    نحيط سيادتكم علماً بأن الطالب حصل على *{exam.score}* من *{exam.max}* في *{exam.name}*.

                    لأي استفسار يمكنكم مراسلتنا على رقم المكتب *{teacher.office_phone}*.
                    شكرا""";
            case REPORT -> """
                    السيد ولي أمر الطالب : *{student.name}*
                    مرفق مع الرسالة تقرير الطالب حتى تاريخ *{date}*.

                    لأي استفسار يمكنكم مراسلتنا على رقم المكتب *{teacher.office_phone}*.
                    شكرا""";
            default -> """
                    السيد ولي أمر الطالب : *{student.name}*
                    نحيط سيادتكم علماً بأن الطالب حضر حصة *{lesson.name}* اليوم في تمام الساعة *{attendance.time}*.

                    لأي استفسار يمكنكم مراسلتنا على رقم المكتب *{teacher.office_phone}*.
                    شكرا""";
        };
    }

    private static MessageVariant newVariant(UUID automationId, String body, int sortOrder) {
        MessageVariant v = new MessageVariant();
        v.setAutomationId(automationId);
        v.setBody(body);
        v.setSortOrder(sortOrder);
        return v;
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
     * students meant forty sequential WhatsApp round trips with a Hikari
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
        MessageText.Teacher teacher = teacher();
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
        MessageText.Teacher teacher = teacher();
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
                        lectureId, groupId, byUser, byName, m.vars());
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
                    null, null, m.vars());
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
                a.getAudience(), teacher());
    }

    /**
     * One student's message, fully resolved: every entity read and every
     * placeholder substituted, so nothing here needs a database session again.
     * That is what lets the send phase run with no connection held.
     */
    record PlannedMessage(List<WhatsappLogSender.Recipient> recipients, String body,
            String origin, Map<String, String> vars) {
    }

    /**
     * Renders a random variant for one student and names every recipient the
     * template targets (parent and/or student). Reads only - the send happens in
     * {@link #deliver}, after the transaction that called this has closed.
     */
    private PlannedMessage plan(List<MessageVariant> variants, Student s,
            Group group, Lecture lecture, Registration registration, String status, String origin,
            MessageAudience audience, MessageText.Teacher teacher) {
        Map<String, String> vars = MessageText.studentVars(s, teacher);
        // The group the lesson was attended under wins over the student's own.
        MessageText.putGroup(vars, group == null ? s.getGroup() : group);
        MessageText.putLesson(vars, lecture);
        MessageText.putAttendance(vars, registration, status);
        MessageText.putExam(vars, registration, lecture);
        MessageVariant picked = randomVariant(variants);
        String code = s.getSerial() == null ? "" : String.valueOf(s.getSerial());
        // The vars travel with the message: the rendered body is what the history
        // records, but the send fills the template's numbered placeholders from
        // them, and by the send phase the entities they came from are long gone.
        return new PlannedMessage(recipients(s, code, audience),
                MessageText.render(picked.getBody(), vars), origin, vars);
    }

    /** Renders and sends in one step. Used by the single-student automatic path. */
    private boolean dispatch(List<MessageVariant> variants, Student s,
            Group group, Lecture lecture, Registration registration, String status,
            String source, String origin,
            MessageAudience audience, UUID lectureId, UUID groupId, UUID byUser, String byName,
            MessageText.Teacher teacher) {
        PlannedMessage m = plan(variants, s, group, lecture, registration, status, origin,
                audience, teacher);
        boolean any = false;
        for (WhatsappLogSender.Recipient r : m.recipients()) {
            any |= logSender.logAndSend(r, m.body(), source, origin, lectureId, groupId,
                    byUser, byName, m.vars());
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
     * and the upload of that PDF to WhatsApp, once per recipient. A
     * pooled connection was held across both, on the async pool, once per
     * student created - so a bulk intake of new students could occupy several
     * connections at a time doing no database work whatsoever.
     */
    public void sendNewStudent(UUID studentId, UUID byUserId) {
        PlannedCard planned = self.planNewStudent(studentId, byUserId);
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
        boolean any = false;
        for (WhatsappLogSender.Recipient r : planned.recipients()) {
            any |= logSender.logAndSendFile(r, planned.body(), card, fileName, "SYSTEM",
                    "NEW_STUDENT", planned.vars());
        }
        // The card went out on its own, so this student is no longer waiting for
        // one - the resend button must skip them exactly as it skips a student
        // whose card was pressed by hand. Stamped only on a real delivery: a
        // failed welcome leaves them pending, which is the point.
        if (any) {
            self.markBarcodeSent(planned.studentId());
        }
    }

    /**
     * Record that a student's barcode card reached them.
     *
     * <p>Its own short transaction on purpose. Every caller is a send path that
     * is deliberately NOT transactional - they render a PDF and talk to WhatsApp,
     * and a pooled connection has no business being held across either.
     */
    @Transactional
    public void markBarcodeSent(UUID studentId) {
        if (studentId != null) {
            studentRepository.markBarcodeSent(studentId, java.time.OffsetDateTime.now());
        }
    }

    /** The welcome message and who gets it, resolved but not yet rendered or sent. */
    record PlannedCard(List<WhatsappLogSender.Recipient> recipients, String body, UUID studentId,
            Map<String, String> vars) {
    }

    @Transactional(readOnly = true)
    public PlannedCard planNewStudent(UUID studentId, UUID byUserId) {
        if (studentId == null) {
            return null;
        }
        // The switch, and it hangs off the ACCOUNT that entered the student, not
        // the workspace. One teacher's assistants work differently from each
        // other: whoever is on the desk taking walk-ins wants the card to leave
        // with the student, whoever is importing last year's roster does not,
        // and a shared setting would have each of them turning the other's off.
        //
        // A create with no account behind it - a self-signup, the offline replay
        // running before anybody signed in - sends nothing. Silence is the right
        // answer when there is no one whose preference could have said otherwise.
        if (byUserId == null || !userRepository.findById(byUserId)
                .map(com.center.user.entity.User::isBarcodeAutoSend).orElse(false)) {
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
        Map<String, String> vars = MessageText.studentVars(s, teacher());
        MessageVariant picked = randomVariant(variants);
        String body = MessageText.render(picked.getBody(), vars);
        String code = s.getSerial() == null ? "" : String.valueOf(s.getSerial());
        // To the STUDENT, not to the template's audience - see BARCODE_AUDIENCE.
        return new PlannedCard(recipients(s, code, BARCODE_AUDIENCE), body, studentId, vars);
    }

    /** One barcode-card send: whether it went, to whom, and why it did not. */
    public record BarcodeSendResult(boolean sent, String phone, String reason) {
    }

    /**
     * Send the barcode card to one student, on the teacher's say-so (the barcode
     * button). Uses the SAME NEW_STUDENT text the automatic welcome uses - the
     * manual button and the on-create message carry one message, written once -
     * but skips the automatic path's enabled/once-ever guards, since this is an
     * explicit resend.
     *
     * <p>Never throws on a refusal by WhatsApp: the caller shows the reason, and
     * a reason a teacher can act on ("bind this type to a template") is worth
     * more than a 500 that reads as if the system broke.
     */
    public BarcodeSendResult sendBarcode(UUID studentId) {
        PlannedCard planned = self.planBarcodeCard(studentId);
        byte[] card;
        String fileName;
        try {
            card = barcodeService.renderPdf(studentId);
            fileName = barcodeService.fileName(studentId);
        } catch (RuntimeException ex) {
            card = null;
            fileName = null;
        }
        boolean any = false;
        String phone = null;
        String reason = null;
        for (WhatsappLogSender.Recipient r : planned.recipients()) {
            WhatsappLogSender.Outcome outcome = logSender.sendFile(r, planned.body(), card,
                    fileName, "MANUAL", "BARCODE", planned.vars());
            any |= outcome.sent();
            if (phone == null) {
                phone = r.phone();
            }
            if (!outcome.sent() && reason == null) {
                reason = outcome.failureReason();
            }
        }
        if (any) {
            self.markBarcodeSent(studentId);
        }
        return new BarcodeSendResult(any, phone, any ? null : reason);
    }

    /**
     * The barcode card's text and recipient, resolved from the NEW_STUDENT
     * template. Requires the text to be written (throws the same "write the
     * message first" error the other manual sends do).
     *
     * <p>The card goes to the student - see {@link #BARCODE_AUDIENCE} - which is
     * also who the automatic welcome sends it to, so the button and the message
     * it repeats can never reach different people.
     */
    @Transactional(readOnly = true)
    public PlannedCard planBarcodeCard(UUID studentId) {
        Student s = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("الطالب غير موجود"));
        MessageAutomation a = requireAutomation(AutomationType.NEW_STUDENT);
        List<MessageVariant> variants = requireVariants(a);
        Map<String, String> vars = MessageText.studentVars(s, teacher());
        MessageVariant picked = randomVariant(variants);
        String body = MessageText.render(picked.getBody(), vars);
        String code = s.getSerial() == null ? "" : String.valueOf(s.getSerial());
        return new PlannedCard(recipients(s, code, BARCODE_AUDIENCE), body, studentId, vars);
    }

    // ── The barcode card, in bulk ─────────────────────────────────────────

    /**
     * How many students are still waiting for their card, and whether anything
     * could be sent to them at all.
     */
    public record BarcodeBacklog(long pending, String blockedReason) {
    }

    /** One batch of the bulk send, and what is left after it. */
    public record BarcodeBatchResult(int sent, int failed, long remaining, String blockedReason) {
    }

    /**
     * Whether adding a student from THIS account sends them their card at once.
     *
     * <p>Per account, not per workspace. A teacher and their assistants share a
     * roster but not a job: the one on the desk taking walk-ins wants the card to
     * leave with the student, the one importing last year's list does not, and a
     * single shared switch would have them turning each other's off - or worse,
     * discovering mid-import that somebody already had.
     *
     * <p>An unauthenticated caller reads OFF. Nothing sends without an account
     * behind it, so that is the truthful answer rather than a refusal.
     */
    @Transactional(readOnly = true)
    public boolean barcodeAutoSend() {
        UUID me = com.center.auth.security.AuthenticatedUser.currentId();
        return me != null && userRepository.findById(me)
                .map(com.center.user.entity.User::isBarcodeAutoSend).orElse(false);
    }

    /** Turn it on or off for the signed-in account only. */
    @Transactional
    public boolean setBarcodeAutoSend(boolean on) {
        UUID me = com.center.auth.security.AuthenticatedUser.currentId();
        if (me == null) {
            throw new BusinessRuleException("لا يمكن حفظ الإعداد بدون تسجيل دخول");
        }
        com.center.user.entity.User u = userRepository.findById(me)
                .orElseThrow(() -> new ResourceNotFoundException("الحساب غير موجود"));
        u.setBarcodeAutoSend(on);
        userRepository.save(u);
        return on;
    }

    @Transactional(readOnly = true)
    public BarcodeBacklog barcodeBacklog() {
        return new BarcodeBacklog(studentRepository.countPendingBarcode(adminId()),
                barcodeBlockedReason());
    }

    /**
     * Send the card to the next {@code limit} students who have never received
     * one, oldest student code first.
     *
     * <p><b>Why a batch and not "all of them".</b> Each card is an openhtmltopdf
     * render, a media upload and a send - seconds each, and this runs the first
     * time over a roster that has never had the button. Doing two hundred inside
     * one HTTP request would sit past every timeout between here and the browser
     * with nothing to show for the wait. The caller loops instead, and each round
     * trip reports what it did, so the progress on screen is real.
     *
     * <p>Resuming is free and needs no cursor: a student is stamped the moment
     * their card lands, so the next batch simply asks the same question again and
     * gets whoever is left. A student who FAILS is deliberately not stamped and
     * will be picked up again - which is why the caller stops when a whole batch
     * sends nothing, rather than looping on a backlog that cannot move.
     */
    public BarcodeBatchResult sendPendingBarcodes(int limit) {
        UUID admin = adminId();
        // Asked once, before any work: with no template bound there is nothing to
        // send with, and grinding through the roster to write one identical failed
        // row per student would spend minutes to say what is known up front.
        String blocked = barcodeBlockedReason();
        if (blocked != null) {
            return new BarcodeBatchResult(0, 0, studentRepository.countPendingBarcode(admin),
                    blocked);
        }
        int sent = 0;
        int failed = 0;
        for (UUID studentId : studentRepository.findPendingBarcodeIds(admin, Math.max(1, limit))) {
            try {
                if (sendBarcode(studentId).sent()) {
                    sent++;
                } else {
                    failed++;
                }
            } catch (ResourceNotFoundException ex) {
                // The row went between listing the batch and reaching it. One
                // student's disappearance is not a reason to abandon the other
                // nineteen. A BusinessRuleException is deliberately NOT caught:
                // every one raised here is about the workspace, not the student
                // (the message text is not written yet), so it is true of all of
                // them and the teacher should hear it once, straight away.
                failed++;
            }
        }
        return new BarcodeBatchResult(sent, failed, studentRepository.countPendingBarcode(admin),
                null);
    }

    /**
     * Send the card to exactly these students.
     *
     * <p>The difference from {@link #sendPendingBarcodes(int)} is who chooses the
     * recipients. That one picks them itself, which is right for "catch everyone
     * up" but wrong for a button whose whole purpose is that the teacher looked
     * at a list first: if the server re-derived the batch, the send could cover
     * someone the screen was not showing, and the preview would be decoration.
     * Here the screen's list IS the argument.
     *
     * <p>Ids from another workspace resolve to nothing - the lookup behind
     * {@link #sendBarcode(UUID)} is tenant-scoped - so a forged id is a failure
     * for that one student, never a leak.
     *
     * <p>Still batched: the caller sends a slice at a time for the same reason,
     * each card being a render, an upload and a send.
     */
    public BarcodeBatchResult sendBarcodes(List<UUID> ids) {
        UUID admin = adminId();
        String blocked = barcodeBlockedReason();
        if (blocked != null) {
            return new BarcodeBatchResult(0, 0, studentRepository.countPendingBarcode(admin),
                    blocked);
        }
        int sent = 0;
        int failed = 0;
        for (UUID studentId : ids) {
            try {
                if (sendBarcode(studentId).sent()) {
                    sent++;
                } else {
                    failed++;
                }
            } catch (ResourceNotFoundException ex) {
                failed++;
            }
        }
        return new BarcodeBatchResult(sent, failed, studentRepository.countPendingBarcode(admin),
                null);
    }

    /** Why no card can be sent right now - no number, no template - or null. */
    private String barcodeBlockedReason() {
        // findFirst() AFTER the map, never before it. Stream.findFirst() wraps its
        // element in Optional.of, which throws on null - and null is exactly what
        // blockedReason() returns when the type is fine. Mapped first, this method
        // threw a NullPointerException in the one case it was written to report:
        // everything configured and ready to send.
        return availability.messageTypes(adminId()).stream()
                .filter(t -> WhatsappResponsibilityCatalog.BARCODE.equals(t.code()))
                .findFirst()
                .map(WhatsappResponsibilityResponse::blockedReason)
                .orElse(null);
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
        logSender.logAndSendFile(r, planned.body(), pdf, fileName, "MANUAL", "REPORT",
                planned.vars());
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
        Map<String, String> vars = MessageText.studentVars(s, teacher());
        MessageVariant picked = randomVariant(variants);
        String body = MessageText.render(picked.getBody(), vars);
        String code = s.getSerial() == null ? "" : String.valueOf(s.getSerial());
        WhatsappLogSender.Recipient r = new WhatsappLogSender.Recipient(
                s.getName(), phone, code, toParent ? "PARENT" : "STUDENT", s.getId());
        return new PlannedCard(java.util.List.of(r), body, studentId, vars);
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
        MessageText.Teacher teacher = teacher();
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
        return automationRepository.findByType(type).orElseGet(() -> self.provision(type));
    }

    /**
     * The wordings this type may be logged under. Never empty: a workspace that
     * has none is provisioned one here rather than refused, since there is no
     * longer any screen on which a person could go and write it.
     */
    private List<MessageVariant> requireVariants(MessageAutomation a) {
        List<MessageVariant> variants = bodies(a);
        if (variants.isEmpty()) {
            self.provision(a.getType());
            variants = bodies(a);
        }
        return variants;
    }

    /** The type's variants that actually carry text; a blank one renders nothing. */
    private List<MessageVariant> bodies(MessageAutomation a) {
        return variantRepository.findByAutomationIdOrderBySortOrder(a.getId()).stream()
                .filter(v -> v.getBody() != null && !v.getBody().isBlank())
                .toList();
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

    // ── Manual send ───────────────────────────────────────────────────────

    /** One resolved recipient with its already-rendered message body. */
    record Pending(WhatsappLogSender.Recipient recipient, String body, Map<String, String> vars) {}


    /**
     * The workspace owner - the teacher the student belongs to, NOT whoever is
     * signed in. An assistant sending on the teacher's behalf must produce a
     * message (and a barcode, a report, an invoice) carrying the teacher's name;
     * the tenant is exactly that, for an admin and an assistant alike.
     */
    private MessageText.Teacher teacher() {
        return userRepository.findById(adminId())
                .map(u -> new MessageText.Teacher(
                        u.getUsername() == null || u.getUsername().isBlank()
                                ? settingsService.senderName() : u.getUsername(),
                        u.getOfficePhone()))
                .orElseGet(() -> new MessageText.Teacher(settingsService.senderName(), null));
    }

    /**
     * The name of a student's approved guardian account, when one exists. A
     * student record holds the guardian's NUMBER but never their name, so this is
     * the only place the {parent.name} variable can be filled from - and it stays
     * blank rather than guessing when no parent has linked themselves.
     */

    // ── History ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<WhatsappMessageLogResponse> log(Pageable pageable) {
        // The numbers are read once for the page rather than per row: a page of
        // fifty messages usually came from two or three of them.
        Map<UUID, String> names = new java.util.HashMap<>();
        for (WhatsappInstance w : instances.numbers(adminId())) {
            names.put(w.getId(), numberName(w));
        }
        return logRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(m -> toLogResponse(m, names));
    }

    /**
     * Deletes this workspace's send history, all of it or one date range.
     *
     * <p>Irreversible and deliberately so - the point of the button is to get rid
     * of the rows, not to hide them. It touches ONLY the history: the messages
     * were already delivered, and nothing else reads these rows except the
     * "already messaged about this lesson" checks, which will simply stop
     * recognising the cleared period and allow a resend.
     *
     * @param from first day to delete, in Cairo dates; null with {@code to} null
     *             means everything
     * @return how many rows went
     */
    @Transactional
    public int clearLog(LocalDate from, LocalDate to) {
        UUID admin = adminId();
        int gone = from == null || to == null
                ? logRepository.purgeAll(admin)
                : logRepository.purgeRange(admin, from, to);
        log.info("whatsapp log cleared [{}]: {} rows ({})", admin, gone,
                from == null || to == null ? "all" : from + ".." + to);
        return gone;
    }

    private static WhatsappMessageLogResponse toLogResponse(WhatsappMessageLog m,
            Map<UUID, String> names) {
        UUID instanceId = m.getInstanceId();
        return new WhatsappMessageLogResponse(m.getId(), m.getRecipientName(), m.getPhone(),
                m.getRecipientCode(), m.getRecipientType(), m.getBody(), m.getStatus(),
                m.getFailureReason(), m.getSource(), m.getOrigin(),
                instanceId == null ? null : names.get(instanceId),
                m.getTemplateName(),
                m.getSentByName(), m.getCreatedAt());
    }

    private static String numberName(WhatsappInstance w) {
        if (w.getLabel() != null && !w.getLabel().isBlank()) {
            return w.getLabel();
        }
        if (w.getDisplayName() != null && !w.getDisplayName().isBlank()) {
            return w.getDisplayName();
        }
        return w.getPhone() != null && !w.getPhone().isBlank() ? "+" + w.getPhone() : "رقم واتساب";
    }
}
