package com.center.admin.service;
import com.center.google.event.GoogleContactEvents;
import com.center.google.entity.GoogleContactsConfig;
import com.center.google.repository.GoogleContactsConfigRepository;
import com.center.student.repository.StudentRepository;
import com.center.whatsapp.entity.WhatsappConfig;
import com.center.whatsapp.repository.WhatsappConfigRepository;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.notification.dto.BroadcastRequest;
import com.center.admin.dto.CreateAdminRequest;
import com.center.admin.dto.SuperParentUpdateRequest;
import com.center.admin.dto.SuperStudentUpdateRequest;
import com.center.admin.dto.UpdateAdminRequest;
import com.center.admin.dto.AdminSummaryResponse;
import com.center.admin.dto.AssistantAdminResponse;
import com.center.notification.dto.BroadcastResult;
import com.center.parent.dto.LinkedPersonResponse;
import com.center.notification.dto.OutgoingMessageResponse;
import com.center.admin.dto.ParentAdminResponse;
import com.center.admin.dto.ParentDetailResponse;
import com.center.admin.dto.StudentAdminResponse;
import com.center.admin.dto.StudentDetailResponse;
import com.center.user.dto.UserSearchResponse;
import com.center.admin.entity.AdminModule;
import com.center.admin.entity.Module;
import com.center.notification.entity.OutgoingMessage;
import com.center.parent.entity.Parent;
import com.center.user.entity.User;
import com.center.common.enums.NotificationType;
import com.center.common.enums.Role;
import com.center.common.exception.BusinessRuleException;
import com.center.common.exception.DuplicateResourceException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.admin.repository.AdminModuleRepository;
import com.center.admin.repository.ModuleRepository;
import com.center.parent.repository.ParentRepository;
import com.center.admin.repository.SuperAdminRepository;
import com.center.admin.repository.SuperAdminRepository.AdminSummaryRow;
import com.center.admin.repository.SuperAdminRepository.NameRow;
import com.center.admin.repository.SuperAdminRepository.ParentDetailRow;
import com.center.admin.repository.SuperAdminRepository.StudentDetailRow;
import com.center.notification.repository.NotificationRepository;
import com.center.notification.repository.OutgoingMessageRepository;
import com.center.user.repository.UserRepository;
import com.center.whatsapp.service.GreenApiClient;
import com.center.notification.service.NotificationService;
import com.center.settings.service.SettingsService;
import com.center.admin.service.SuperAdminService;
import com.center.notification.service.VariableCatalog;
import com.center.common.util.PhotoCodec;
import com.center.common.validation.EmailPolicy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SuperAdminServiceImpl implements SuperAdminService {

    private static final String NOT_FOUND = "المدرّس غير موجود";
    private static final String USER_NOT_FOUND = "المستخدم غير موجود";
    private static final String NAME_TAKEN = "اسم المدرّس مستخدم بالفعل";
    private static final String EMAIL_TAKEN = "هذا البريد الإلكتروني مستخدم بالفعل";

    private final SuperAdminRepository superAdminRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ModuleRepository moduleRepository;
    private final AdminModuleRepository adminModuleRepository;
    private final ParentRepository parentRepository;
    private final NotificationService notificationService;
    private final SettingsService settingsService;
    private final GreenApiClient greenApiClient;
    private final OutgoingMessageRepository outgoingMessageRepository;
    private final NotificationRepository notificationRepository;
    private final com.center.student.repository.StudentRepository studentRepository;
    private final com.center.whatsapp.repository.WhatsappConfigRepository whatsappConfigRepo;
    private final org.springframework.context.ApplicationEventPublisher events;

    /**
     * Routes the transactional halves of {@link #broadcast} through the proxy;
     * a plain {@code this.} call would bypass it and put the WhatsApp fan-out
     * straight back inside the transaction it was just taken out of.
     */
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private SuperAdminServiceImpl self;

    @Override
    @Transactional(readOnly = true)
    public List<AdminSummaryResponse> listAdmins(String q) {
        return superAdminRepository.findAdminSummaries(search(q)).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StudentAdminResponse> listStudents(String q, UUID teacherId, String grade, String gender,
            Boolean registered, Boolean active, Pageable pageable) {
        return superAdminRepository.findStudentSummaries(search(q),
                teacherId == null ? null : teacherId.toString(), search(grade), search(gender),
                registered, active, pageable)
                .map(r -> new StudentAdminResponse(
                        r.getId(), r.getName(), r.getGrade(), r.getSerial(), r.getActive(),
                        r.getTeacher(), r.getPhones(), r.getParentPhones(), r.getUserId() != null,
                        r.getGender(), offset(r.getCreatedAt()), r.getCreatedBy(),
                        offset(r.getUpdatedAt()), r.getUpdatedBy()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> listStudentGrades() {
        return superAdminRepository.findDistinctGrades();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ParentAdminResponse> listParents(String q, Pageable pageable) {
        return superAdminRepository.findParentSummaries(search(q), pageable)
                .map(r -> new ParentAdminResponse(
                        r.getId(), r.getName(), r.getPhone(), r.getSerial(), r.getActive(),
                        r.getStudentCount(), r.getUserId() != null,
                        offset(r.getCreatedAt()), r.getCreatedBy(),
                        offset(r.getUpdatedAt()), r.getUpdatedBy()));
    }

    @Override
    @Transactional
    public void updateStudent(UUID studentId, SuperStudentUpdateRequest request) {
        if (superAdminRepository.findStudentDetail(studentId) == null) {
            throw new ResourceNotFoundException(USER_NOT_FOUND);
        }
        superAdminRepository.updateStudent(studentId, request.name().strip(), search(request.grade()),
                request.genderValue(), request.religionValue(), request.academicTrackValue(),
                search(request.school()), search(request.city()), request.birthDate(),
                request.lessonPrice(), request.discounted(), search(request.notes()),
                request.studentPhonesCsv(), request.parentPhonesCsv());
        studentRepository.findAdminIdById(studentId).ifPresent(adminId ->
                events.publishEvent(new com.center.google.event.GoogleContactEvents.StudentChanged(adminId, studentId)));
    }

    @Override
    @Transactional
    public void deleteStudent(UUID studentId) {
        if (superAdminRepository.findStudentDetail(studentId) == null) {
            throw new ResourceNotFoundException(USER_NOT_FOUND);
        }
        superAdminRepository.deleteStudentRegistrations(studentId);
        superAdminRepository.deleteStudentAttendance(studentId);
        superAdminRepository.deleteStudentLinks(studentId);
        superAdminRepository.deleteStudentRow(studentId);
    }

    @Override
    @Transactional
    public void updateParent(UUID parentId, SuperParentUpdateRequest request) {
        if (!parentRepository.existsById(parentId)) {
            throw new ResourceNotFoundException(USER_NOT_FOUND);
        }
        superAdminRepository.updateParent(parentId, request.name().strip(), request.phone().strip());
        events.publishEvent(new com.center.google.event.GoogleContactEvents.ParentChanged(parentId));
    }

    @Override
    @Transactional
    public void deleteParent(UUID parentId) {
        Parent parent = parentRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
        UUID accountId = parent.getUserId();
        superAdminRepository.deleteParentLinks(parentId);
        superAdminRepository.deleteParentRow(parentId);
        // The login account last; its notifications/sessions cascade off it.
        if (accountId != null) {
            userRepository.deleteById(accountId);
        }
    }

    private static String search(String q) {
        return q == null || q.isBlank() ? null : q.strip();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssistantAdminResponse> listAssistants(UUID adminId) {
        return superAdminRepository.findAssistants(adminId).stream()
                .map(a -> new AssistantAdminResponse(
                        a.getId(), a.getUsername(), a.getEmail(), a.getActive(),
                        PhotoCodec.toDataUrl(a.getPhotoData(), a.getPhotoType())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StudentDetailResponse getStudentDetail(UUID studentId) {
        StudentDetailRow row = superAdminRepository.findStudentDetail(studentId);
        if (row == null) {
            throw new ResourceNotFoundException(USER_NOT_FOUND);
        }
        return new StudentDetailResponse(
                row.getId(), row.getName(), row.getGrade(), row.getSerial(), row.getActive(),
                row.getTeacher(), row.getPhones(), row.getParentPhones(), row.getUserId(),
                PhotoCodec.toDataUrl(row.getPhotoData(), row.getPhotoType()),
                row.getGender(), row.getReligion(), row.getAcademicTrack(), row.getSchool(),
                row.getCity(), row.getBirthDate(), row.getLessonPrice(), row.getDiscounted(),
                row.getNotes(), links(superAdminRepository.findStudentParents(studentId)));
    }

    @Override
    @Transactional(readOnly = true)
    public ParentDetailResponse getParentDetail(UUID parentId) {
        ParentDetailRow row = superAdminRepository.findParentDetail(parentId);
        if (row == null) {
            throw new ResourceNotFoundException(USER_NOT_FOUND);
        }
        return new ParentDetailResponse(
                row.getId(), row.getName(), row.getPhone(), row.getSerial(), row.getActive(),
                row.getUserId(), PhotoCodec.toDataUrl(row.getPhotoData(), row.getPhotoType()),
                links(superAdminRepository.findParentStudents(parentId)));
    }

    @Override
    @Transactional
    public void setStudentActive(UUID studentId, boolean active) {
        if (superAdminRepository.findStudentDetail(studentId) == null) {
            throw new ResourceNotFoundException(USER_NOT_FOUND);
        }
        superAdminRepository.updateStudentActive(studentId, active);
    }

    @Override
    @Transactional
    public void setParentActive(UUID parentId, boolean active) {
        Parent parent = parentRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
        User account = userRepository.findById(parent.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
        account.setActive(active);
        userRepository.save(account);
    }

    private static List<LinkedPersonResponse> links(List<NameRow> rows) {
        return rows.stream().map(r -> new LinkedPersonResponse(r.getName(), r.getDetail())).toList();
    }

    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "MUSLIMS", "المسلمون",
            "CHRISTIANS", "المسيحيون",
            "MALE_STUDENTS", "الطلاب الذكور",
            "FEMALE_STUDENTS", "الطالبات",
            "ALL_TEACHERS", "كل المدرّسين");

    private static final Map<DayOfWeek, String> ARABIC_DAYS = Map.of(
            DayOfWeek.SATURDAY, "السبت", DayOfWeek.SUNDAY, "الأحد", DayOfWeek.MONDAY, "الاثنين",
            DayOfWeek.TUESDAY, "الثلاثاء", DayOfWeek.WEDNESDAY, "الأربعاء", DayOfWeek.THURSDAY, "الخميس",
            DayOfWeek.FRIDAY, "الجمعة");

    /** One WhatsApp message the broadcast still owes, resolved but not yet sent. */
    private record PendingSend(String phone, String body) {
    }

    /** What {@link #planBroadcast} produced: the log row and the sends it owes. */
    private record Planned(UUID outgoingId, int recipients, List<PendingSend> sends) {
    }

    /**
     * Deliberately NOT {@code @Transactional} - it used to be, and that is the
     * widest-reaching instance of the problem in the codebase. A super-admin
     * broadcast targets every recipient across EVERY workspace, and each one was
     * a sequential WhatsApp round trip made inside a single write transaction.
     * One of eight pooled connections was therefore held for as long as the
     * whole fan-out took, on a request thread, while every other request in the
     * system - every workspace's - queued behind it.
     *
     * <p>Resolve and persist in one short transaction, send holding nothing,
     * then record the count. The in-app notifications commit before the first
     * WhatsApp call, so the part that must not be lost is durable earlier than
     * it was before.
     */
    @Override
    public BroadcastResult broadcast(BroadcastRequest request) {
        Planned planned = self.planBroadcast(request);
        int whatsappSent = 0;
        for (PendingSend send : planned.sends()) {
            try {
                greenApiClient.sendText("broadcast", send.phone(), send.body());
                whatsappSent++;
            } catch (RuntimeException e) {
                // A single WhatsApp failure must not abort the whole broadcast.
                log.warn("WhatsApp broadcast to a recipient failed: {}", e.getMessage());
            }
        }
        self.recordSent(planned.outgoingId(), whatsappSent);
        return new BroadcastResult(planned.recipients(), whatsappSent);
    }

    /** Persist the log row and every in-app notification; collect what to send. */
    @Transactional
    public Planned planBroadcast(BroadcastRequest request) {
        Set<UUID> recipients = resolveRecipients(request);
        recipients.remove(null);
        if (recipients.isEmpty()) {
            throw new BusinessRuleException("لا يوجد مستلمون مطابقون للتحديد");
        }
        String sender = settingsService.senderName();
        String rawTitle = request.title().strip();
        String rawBody = request.body().strip();

        // The message text is rendered per recipient: {student.name}, {parent.phone},
        // {day}, ... resolve to that person's data (or blank when it does not apply).
        Map<String, String> global = globalVars(sender);
        Map<UUID, Map<String, String>> perUser = recipientVars(recipients);
        Map<String, String> base = new HashMap<>();
        for (String key : VariableCatalog.keys()) {
            base.put(key, "");
        }

        // Persist the broadcast log first so its id can tag every per-recipient
        // notification, making the whole broadcast deletable from every inbox later.
        OutgoingMessage record = new OutgoingMessage();
        record.setChannel(request.whatsapp() ? "whatsapp" : "notification");
        record.setSender(sender);
        record.setTitle(rawTitle);
        record.setBody(rawBody);
        record.setAudience(audienceSummary(request));
        record.setRecipients(recipients.size());
        record.setWhatsappSent(0);
        outgoingMessageRepository.save(record);
        UUID outgoingId = record.getId();

        List<PendingSend> sends = new ArrayList<>();
        for (UUID recipientId : recipients) {
            Map<String, String> vars = new HashMap<>(base);
            vars.putAll(global);
            Map<String, String> mine = perUser.get(recipientId);
            if (mine != null) {
                vars.putAll(mine);
            }
            // Curly-brace markers that survive interpolation are literal emphasis
            // markers, not variables: they must never reach the recipient as "{...}".
            // In-app notifications drop them; WhatsApp turns them into *bold*.
            String title = stripMarkers(interpolate(rawTitle, vars), false);
            String rendered = interpolate(rawBody, vars);
            String body = stripMarkers(rendered, false);
            notificationService.notify(recipientId, sender, NotificationType.SYSTEM_CENTER, title, body,
                    null, outgoingId);

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

    private static Map<String, String> globalVars(String sender) {
        LocalDate date = LocalDate.now(ZoneId.of("Africa/Cairo"));
        LocalTime time = LocalTime.now(ZoneId.of("Africa/Cairo"));
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
        for (SuperAdminRepository.SimpleUserRow r : superAdminRepository.findTeacherVars(ids)) {
            perUser.computeIfAbsent(r.getUserId(), k -> new HashMap<>()).put("teacher.name", nz(r.getName()));
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

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Text wrapped in {curly braces} = emphasis marker. */
    private static final Pattern MARKER = Pattern.compile("\\{([^{}]+)\\}");

    /**
     * Strips leftover emphasis markers from a fully-interpolated message. For
     * WhatsApp the wrapped text becomes *bold*; for in-app text the braces are
     * simply removed.
     */
    private static String stripMarkers(String text, boolean whatsapp) {
        if (text == null) {
            return null;
        }
        return MARKER.matcher(text).replaceAll(whatsapp ? "*$1*" : "$1");
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b != null && !b.isBlank() ? b : null;
    }

    private Set<UUID> resolveRecipients(BroadcastRequest request) {
        Set<UUID> set = new HashSet<>();
        if (request.categories() != null) {
            for (String category : request.categories()) {
                switch (category) {
                    case "MUSLIMS" -> set.addAll(superAdminRepository.findStudentUserIdsByReligion("مسلم"));
                    case "CHRISTIANS" -> set.addAll(superAdminRepository.findStudentUserIdsByReligion("مسيحي"));
                    case "MALE_STUDENTS" -> set.addAll(superAdminRepository.findStudentUserIdsByGender("ذكر"));
                    case "FEMALE_STUDENTS" -> set.addAll(superAdminRepository.findStudentUserIdsByGender("أنثى"));
                    case "ALL_TEACHERS" -> set.addAll(ids(userRepository.findByRoleAndActiveTrue(Role.ADMIN)));
                    default -> { }
                }
            }
        }
        if (request.grades() != null) {
            for (String grade : request.grades()) {
                set.addAll(superAdminRepository.findStudentUserIdsByGrade(grade));
            }
        }
        if (request.teacherId() != null) {
            set.add(request.teacherId());
        }
        if (request.studentsOfTeacherId() != null) {
            set.addAll(superAdminRepository.findStudentUserIdsByTeacher(request.studentsOfTeacherId()));
        }
        if (request.userIds() != null) {
            set.addAll(request.userIds());
        }
        return set;
    }

    private static String audienceSummary(BroadcastRequest request) {
        List<String> parts = new ArrayList<>();
        if (request.categories() != null) {
            for (String c : request.categories()) {
                parts.add(CATEGORY_LABELS.getOrDefault(c, c));
            }
        }
        if (request.grades() != null && !request.grades().isEmpty()) {
            parts.addAll(request.grades());
        }
        if (request.teacherId() != null) {
            parts.add("مدرّس محدد");
        }
        if (request.studentsOfTeacherId() != null) {
            parts.add("طلاب مدرّس محدد");
        }
        if (request.userIds() != null && !request.userIds().isEmpty()) {
            parts.add("مستخدمون محددون (" + request.userIds().size() + ")");
        }
        return String.join("، ", parts);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutgoingMessageResponse> listOutgoing() {
        return outgoingMessageRepository.findTop30ByOrderByCreatedAtDesc().stream()
                .map(m -> new OutgoingMessageResponse(m.getId(), m.getChannel(), m.getSender(),
                        m.getTitle(), m.getBody(), m.getAudience(), m.getRecipients(),
                        m.getWhatsappSent(), m.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public void deleteOutgoing(UUID outgoingId) {
        if (!outgoingMessageRepository.existsById(outgoingId)) {
            throw new ResourceNotFoundException("الرسالة غير موجودة");
        }
        // Pull the broadcast from every recipient inbox, then drop the log row.
        notificationRepository.deleteByOutgoingId(outgoingId);
        outgoingMessageRepository.deleteById(outgoingId);
    }

    private static List<UUID> ids(List<User> users) {
        return users.stream().map(User::getId).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSearchResponse> searchUsers(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return userRepository.findTop20ByUsernameContainingIgnoreCaseOrderByUsername(q.strip()).stream()
                .filter(u -> u.getRole() != Role.SUPER_ADMIN)
                .map(u -> new UserSearchResponse(u.getId(), u.getUsername(), u.getRole(),
                        PhotoCodec.toDataUrl(u.getPhotoData(), u.getPhotoType())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminSummaryResponse getAdmin(UUID adminId) {
        AdminSummaryRow row = superAdminRepository.findAdminSummary(adminId);
        if (row == null) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        return toResponse(row);
    }

    @Override
    @Transactional
    public AdminSummaryResponse createAdmin(CreateAdminRequest request) {
        String username = request.username().strip();
        // Display names stay unique across the platform so admins are told apart.
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException(NAME_TAKEN);
        }
        String email = EmailPolicy.build(request.email(), Role.ADMIN);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException(EMAIL_TAKEN);
        }
        User admin = new User();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setPhone(normalizePhone(request.phone()));
        admin.setPasswordHash(passwordEncoder.encode(request.password()));
        admin.setRole(Role.ADMIN);
        // An Admin is the root of its own workspace - it has no owning admin.
        admin.setAdminId(null);
        admin.setActive(true);
        User saved = userRepository.save(admin);
        seedDefaultModules(saved.getId());
        log.info("Super admin created admin '{}'", username);
        return getAdmin(saved.getId());
    }

    /**
     * A new workspace starts with each platform module at its {@code default_enabled}
     * state (non-platform modules are always available, so they need no row). The
     * super admin can adjust these afterwards.
     */
    private void seedDefaultModules(UUID adminId) {
        for (Module module : moduleRepository.findByActiveTrueOrderBySortOrder()) {
            if (!module.isPlatformControlled()) {
                continue;
            }
            AdminModule row = new AdminModule();
            row.setAdminId(adminId);
            row.setModuleId(module.getId());
            row.setEnabled(module.isDefaultEnabled());
            adminModuleRepository.save(row);
        }
    }

    @Override
    @Transactional
    public AdminSummaryResponse updateAdmin(UUID adminId, UpdateAdminRequest request) {
        User admin = requireAdmin(adminId);
        String username = request.username().strip();
        if (userRepository.existsByUsernameAndIdNot(username, adminId)) {
            throw new DuplicateResourceException(NAME_TAKEN);
        }
        String email = EmailPolicy.build(request.email(), Role.ADMIN);
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, adminId)) {
            throw new DuplicateResourceException(EMAIL_TAKEN);
        }
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setPhone(normalizePhone(request.phone()));
        // A blank password means "leave the current one alone".
        if (request.password() != null && !request.password().isBlank()) {
            admin.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        userRepository.save(admin);
        return getAdmin(adminId);
    }

    @Override
    @Transactional
    public void setActive(UUID adminId, boolean active) {
        User admin = requireAdmin(adminId);
        admin.setActive(active);
        userRepository.save(admin);
        log.info("Super admin {} admin '{}'", active ? "activated" : "deactivated", admin.getUsername());
    }

    @Override
    @Transactional
    public void deleteAdmin(UUID adminId) {
        User admin = requireAdmin(adminId);

        // Child rows first, then parents, then assistants, then the Admin - all
        // scoped by admin_id so no other workspace is touched. One transaction:
        // any failure rolls the whole wipe back.
        superAdminRepository.deleteRegistrations(adminId);
        superAdminRepository.deleteAttendance(adminId);
        superAdminRepository.deleteCenterGrades(adminId);
        superAdminRepository.deleteStudents(adminId);
        superAdminRepository.deleteGroups(adminId);
        superAdminRepository.deleteCenters(adminId);
        // Grades are a global master list now (no admin_id), so they are not part
        // of a single workspace's data and must not be deleted here.
        superAdminRepository.deleteLectures(adminId);
        superAdminRepository.deleteAssistants(adminId);
        // The Admin's own work_sessions cascade away with the user row.
        userRepository.delete(admin);

        log.warn("Super admin deleted admin '{}' and its entire workspace", admin.getUsername());
    }

    @Override
    @Transactional
    public void setUserPhoto(UUID userId, String dataUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
        PhotoCodec.Decoded decoded = PhotoCodec.decode(dataUrl);
        user.setPhotoData(decoded.bytes());
        user.setPhotoType(decoded.type());
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void clearUserPhoto(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
        user.setPhotoData(null);
        user.setPhotoType(null);
        userRepository.save(user);
    }

    /** Loads a user and asserts it is an Admin - never a super admin or assistant. */
    private User requireAdmin(UUID adminId) {
        return userRepository.findById(adminId)
                .filter(user -> user.getRole() == Role.ADMIN)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    /**
     * WhatsApp addresses a number as bare digits, so anything a human typed
     * around them (spaces, dashes, a leading +) is stripped here rather than at
     * every send site. Blank means the admin simply has no number yet.
     */
    private static String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    private AdminSummaryResponse toResponse(AdminSummaryRow row) {
        return new AdminSummaryResponse(
                row.getId(),
                row.getUsername(),
                row.getEmail(),
                row.getPhone(),
                row.getActive(),
                offset(row.getCreatedAt()),
                row.getCreatedBy(),
                offset(row.getUpdatedAt()),
                row.getUpdatedBy(),
                row.getStudentCount(),
                row.getAssistantCount(),
                PhotoCodec.toDataUrl(row.getPhotoData(), row.getPhotoType()),
                whatsappConfigRepo.findById(row.getId()).map(c -> c.isEnabled()).orElse(false));
    }

    @Override
    @Transactional
    public void setWhatsappSync(UUID adminId, boolean enabled) {
        if (superAdminRepository.findAdminSummary(adminId) == null) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        com.center.whatsapp.entity.WhatsappConfig cfg = whatsappConfigRepo.findById(adminId)
                .orElseGet(() -> new com.center.whatsapp.entity.WhatsappConfig(adminId, enabled));
        cfg.setEnabled(enabled);
        whatsappConfigRepo.save(cfg);
    }

    /** Null-safe conversion of a native-query {@link Instant} to a UTC offset. */
    private static OffsetDateTime offset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
