package com.center.student.service;
import com.center.google.event.GoogleContactEvents;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.center.common.config.ApplicationProperties;
import com.center.student.dto.ClaimExistingRequest;
import com.center.student.dto.ForgotVerifyRequest;
import com.center.account.dto.SendCodeRequest;
import com.center.student.dto.StudentRegistrationRequest;
import com.center.student.dto.VerifyExistingRequest;
import com.center.group.dto.GroupOptionResponse;
import com.center.auth.dto.LoginResponse;
import com.center.account.dto.SendCodeResponse;
import com.center.student.dto.TeacherOptionResponse;
import com.center.group.entity.Group;
import com.center.student.entity.Student;
import com.center.student.entity.StudentVerificationCode;
import com.center.user.entity.User;
import com.center.common.enums.Role;
import com.center.common.exception.BusinessRuleException;
import com.center.common.exception.DuplicateResourceException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.auth.service.PrincipalViewFactory;
import com.center.grade.repository.GradeRepository;
import com.center.group.repository.GroupRepository;
import com.center.student.repository.StudentRepository;
import com.center.student.repository.StudentRepository.StudentIdentity;
import com.center.student.repository.StudentVerificationCodeRepository;
import com.center.user.repository.UserRepository;
import com.center.auth.security.AuthenticatedUser;
import com.center.auth.security.JwtService;
import com.center.whatsapp.service.GreenApiClient;
import com.center.notification.service.MessageTemplateService;
import com.center.student.service.StudentSignupService;
import com.center.common.tenant.TenantContext;
import com.center.common.tenant.TenantScopedExecutor;
import com.center.common.util.PhotoCodec;
import com.center.common.validation.EmailPolicy;
import com.center.registration.validation.RegistrationValidation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Public student self-registration.
 *
 * <p>These flows have no JWT, so no tenant is bound at request entry. Anything that
 * touches a {@code @TenantId} entity therefore binds the tenant with
 * {@link TenantContext#callAs} and runs the DB work through {@link TenantScopedExecutor}
 * so the Hibernate session opens AFTER the tenant is set - otherwise every scoped query
 * would resolve to {@code NO_TENANT} and come back empty. The tenant always comes from
 * validated input (the chosen teacher, or the workspace that owns a looked-up student),
 * never from caller headers. Global reads used to pick the tenant (teachers list, serial
 * lookup, name-taken check) are native / non-tenant and run outside any scoped tx.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudentSignupServiceImpl implements StudentSignupService {

    private static final String TEACHER_NOT_FOUND = "المدرس غير موجود";
    private static final String STUDENT_NOT_FOUND = "لا يوجد طالب بهذا الكود";
    private static final String ALREADY_REGISTERED = "هذا الطالب لديه حساب بالفعل";
    private static final String NAME_TAKEN = "هذا الاسم مستخدم بالفعل";
    private static final String PASSWORDS_MISMATCH = "كلمتا المرور غير متطابقتين";
    private static final String NO_ACCOUNT = "لا يوجد حساب لهذا الطالب، أنشئ حسابًا أولًا";
    private static final String EMAIL_TAKEN = "هذا البريد الإلكتروني مستخدم بالفعل، اختر اسمًا آخر";
    private static final String PHONE_TAKEN = "رقم هاتف الطالب مسجّل بالفعل لطالب آخر";

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final GroupRepository groupRepository;
    private final GradeRepository gradeRepository;
    private final StudentVerificationCodeRepository codeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PrincipalViewFactory principalViewFactory;
    private final GreenApiClient greenApiClient;
    private final MessageTemplateService templateService;
    private final ApplicationProperties properties;
    private final TenantScopedExecutor tx;
    private final org.springframework.context.ApplicationEventPublisher events;

    private final SecureRandom random = new SecureRandom();

    // --- Dropdown data -----------------------------------------------------

    @Override
    public List<TeacherOptionResponse> teachers() {
        return userRepository.findByRoleAndActiveTrueOrderByUsername(Role.ADMIN).stream()
                .map(t -> new TeacherOptionResponse(t.getId(), t.getUsername(),
                        PhotoCodec.toDataUrl(t.getPhotoData(), t.getPhotoType())))
                .toList();
    }

    @Override
    public List<String> grades(UUID adminId) {
        requireTeacher(adminId);
        // No surrounding @Transactional: the repository call opens its own session,
        // by which point callAs has already bound the tenant.
        return TenantContext.callAs(adminId,
                () -> gradeRepository.findByActiveTrueOrderByName().stream()
                        .map(g -> g.getName())
                        .toList());
    }

    @Override
    public List<GroupOptionResponse> groups(UUID adminId, String grade) {
        requireTeacher(adminId);
        return TenantContext.callAs(adminId,
                () -> groupRepository.findByGradeAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(grade).stream()
                        .map(g -> new GroupOptionResponse(
                                g.getId(), g.getDayOfWeek(), g.getStartTime(), g.getCenterName()))
                        .toList());
    }

    // --- Option 1: existing student, WhatsApp code -------------------------

    @Override
    public SendCodeResponse sendCode(SendCodeRequest request) {
        StudentIdentity identity = studentRepository.findIdentityBySerial(request.serial())
                .orElseThrow(() -> new ResourceNotFoundException(STUDENT_NOT_FOUND));

        return TenantContext.callAs(identity.getAdminId(), () -> tx.inTenantTx(() -> {
            Student student = studentRepository.findById(identity.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(STUDENT_NOT_FOUND));
            if (student.getUserId() != null) {
                throw new BusinessRuleException(ALREADY_REGISTERED);
            }
            String phone = firstPhone(student);

            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            long recent = codeRepository.countByStudentIdAndCreatedAtAfter(identity.getId(), now.minusHours(1));
            if (recent >= properties.registration().maxSendsPerHour()) {
                throw new BusinessRuleException("لقد تجاوزت عدد محاولات الإرسال، حاول لاحقًا");
            }

            int ttlMinutes = properties.registration().codeTtlMinutes();
            String code = String.format("%06d", random.nextInt(1_000_000));

            StudentVerificationCode row = new StudentVerificationCode();
            row.setStudentId(identity.getId());
            row.setCode(code);
            row.setExpiresAt(now.plusMinutes(ttlMinutes));
            row.setCreatedAt(now);
            codeRepository.save(row);

            greenApiClient.sendText("student_verification", phone, templateService.render("student_verification",
                    Map.of("code", code, "minutes", String.valueOf(ttlMinutes))).body());
            log.info("Verification code issued for student serial {}", request.serial());

            return new SendCodeResponse(maskPhone(phone), ttlMinutes * 60);
        }));
    }

    @Override
    public LoginResponse verifyExisting(ClaimExistingRequest request) {
        matchPasswords(request.password(), request.confirmPassword());
        RegistrationValidation.requireStrongPassword(request.password());
        String email = EmailPolicy.build(request.username(), Role.STUDENT);

        StudentIdentity identity = studentRepository.findIdentityBySerial(request.serial())
                .orElseThrow(() -> new ResourceNotFoundException(STUDENT_NOT_FOUND));

        return TenantContext.callAs(identity.getAdminId(), () -> tx.inTenantTx(() -> {
            Student student = studentRepository.findById(identity.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(STUDENT_NOT_FOUND));
            if (student.getUserId() != null) {
                throw new BusinessRuleException(ALREADY_REGISTERED);
            }

            consumeCode(identity.getId(), request.code());

            User account = createAccount(student.getName(), email, request.password(), identity.getAdminId());
            student.setUserId(account.getId());
            studentRepository.save(student);
            events.publishEvent(new com.center.google.event.GoogleContactEvents.StudentChanged(
                    identity.getAdminId(), student.getId()));

            log.info("Existing student serial {} claimed account {}", request.serial(), account.getId());
            return autoLogin(account);
        }));
    }

    // --- Option 2: brand-new student ---------------------------------------

    @Override
    public LoginResponse registerNew(StudentRegistrationRequest request) {
        String name = RegistrationValidation.requireFourPartArabicName(request.fullName());
        String email = EmailPolicy.build(request.username(), Role.STUDENT);
        matchPasswords(request.password(), request.confirmPassword());
        RegistrationValidation.requireStrongPassword(request.password());
        String studentPhone = RegistrationValidation.requirePhone(request.studentPhone(), "الطالب");
        String parentPhone = RegistrationValidation.requirePhone(request.parentPhone(), "ولي الأمر");
        String school = RegistrationValidation.requireSchool(request.school());

        requireTeacher(request.adminId());
        // The display name may repeat - two students can share a full Arabic
        // name. Their own phone number is what must be unique; a parent number
        // is deliberately allowed to repeat (siblings share one).
        if (studentRepository.studentPhoneExistsAnywhere(studentPhone)) {
            throw new DuplicateResourceException(PHONE_TAKEN);
        }

        return TenantContext.callAs(request.adminId(), () -> tx.inTenantTx(() -> {
            // The account (display name = the full Arabic name, login = the email),
            // then the student record inside the chosen teacher's workspace.
            User account = createAccount(name, email, request.password(), request.adminId());

            Group group = groupRepository.findById(request.groupId())
                    .orElseThrow(() -> new ResourceNotFoundException("المجموعة غير موجودة"));
            if (!group.getGrade().equals(request.grade())) {
                throw new BusinessRuleException("المجموعة لا تنتمي للصف المختار");
            }

            Student student = new Student();
            student.setName(name);
            student.setGrade(request.grade().strip());
            student.setBirthDate(request.birthDate());
            student.setSchool(school);
            student.setCity(request.city().strip());
            student.setGroup(group);
            student.setReligion(request.religion());
            student.setStudentPhones(new String[] {studentPhone});
            student.setParentPhones(new String[] {parentPhone});
            student.setActive(true);
            student.setUserId(account.getId());
            studentRepository.save(student);
            events.publishEvent(new com.center.google.event.GoogleContactEvents.StudentChanged(
                    request.adminId(), student.getId()));

            log.info("New student '{}' registered under admin {}", name, request.adminId());
            return autoLogin(account);
        }));
    }

    // --- Forgot password ---------------------------------------------------

    @Override
    public SendCodeResponse sendResetCode(SendCodeRequest request) {
        StudentIdentity identity = studentRepository.findIdentityBySerial(request.serial())
                .orElseThrow(() -> new ResourceNotFoundException(STUDENT_NOT_FOUND));

        return TenantContext.callAs(identity.getAdminId(), () -> tx.inTenantTx(() -> {
            Student student = studentRepository.findById(identity.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(STUDENT_NOT_FOUND));
            if (student.getUserId() == null) {
                throw new BusinessRuleException(NO_ACCOUNT);
            }
            String phone = firstPhone(student);

            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            long recent = codeRepository.countByStudentIdAndCreatedAtAfter(identity.getId(), now.minusHours(1));
            if (recent >= properties.registration().maxSendsPerHour()) {
                throw new BusinessRuleException("لقد تجاوزت عدد محاولات الإرسال، حاول لاحقًا");
            }

            int ttlMinutes = properties.registration().codeTtlMinutes();
            String code = String.format("%06d", random.nextInt(1_000_000));

            StudentVerificationCode row = new StudentVerificationCode();
            row.setStudentId(identity.getId());
            row.setCode(code);
            row.setExpiresAt(now.plusMinutes(ttlMinutes));
            row.setCreatedAt(now);
            codeRepository.save(row);

            greenApiClient.sendText("student_password_reset", phone, templateService.render("student_password_reset",
                    Map.of("code", code, "minutes", String.valueOf(ttlMinutes))).body());
            log.info("Password-reset code issued for student serial {}", request.serial());

            return new SendCodeResponse(maskPhone(phone), ttlMinutes * 60);
        }));
    }

    @Override
    public void verifyResetCode(ForgotVerifyRequest request) {
        StudentIdentity identity = studentRepository.findIdentityBySerial(request.serial())
                .orElseThrow(() -> new ResourceNotFoundException(STUDENT_NOT_FOUND));
        // Peek only - the code is consumed later, when the new password is set.
        TenantContext.callAs(identity.getAdminId(), () -> tx.inTenantTx(() -> {
            validateCode(identity.getId(), request.code(), false);
            return null;
        }));
    }

    @Override
    public LoginResponse resetPassword(VerifyExistingRequest request) {
        matchPasswords(request.password(), request.confirmPassword());
        RegistrationValidation.requireStrongPassword(request.password());

        StudentIdentity identity = studentRepository.findIdentityBySerial(request.serial())
                .orElseThrow(() -> new ResourceNotFoundException(STUDENT_NOT_FOUND));

        return TenantContext.callAs(identity.getAdminId(), () -> tx.inTenantTx(() -> {
            Student student = studentRepository.findById(identity.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(STUDENT_NOT_FOUND));
            if (student.getUserId() == null) {
                throw new BusinessRuleException(NO_ACCOUNT);
            }

            validateCode(identity.getId(), request.code(), true);

            User account = userRepository.findById(student.getUserId())
                    .orElseThrow(() -> new BusinessRuleException(NO_ACCOUNT));
            account.setPasswordHash(passwordEncoder.encode(request.password()));
            userRepository.save(account);

            log.info("Student serial {} reset password for account {}", request.serial(), account.getId());
            return autoLogin(account);
        }));
    }

    // --- Helpers -----------------------------------------------------------

    private void requireTeacher(UUID adminId) {
        userRepository.findById(adminId)
                .filter(u -> u.getRole() == Role.ADMIN && u.isActive())
                .orElseThrow(() -> new ResourceNotFoundException(TEACHER_NOT_FOUND));
    }

    private static String firstPhone(Student student) {
        String[] phones = student.getStudentPhones();
        if (phones == null || phones.length == 0 || phones[0].isBlank()) {
            throw new BusinessRuleException("لا يوجد رقم هاتف مسجّل لهذا الطالب");
        }
        return phones[0];
    }

    /** Validates the newest code for a student, consuming it on success when asked. */
    private void consumeCode(UUID studentId, String entered) {
        validateCode(studentId, entered, true);
    }

    /**
     * Checks the newest code for a student. A wrong code always burns an attempt;
     * {@code consume} marks a correct code used (pass {@code false} to peek - e.g.
     * the reset flow's verify step, which consumes only once the password is set).
     */
    private void validateCode(UUID studentId, String entered, boolean consume) {
        StudentVerificationCode code = codeRepository
                .findFirstByStudentIdOrderByCreatedAtDesc(studentId)
                .orElseThrow(() -> new BusinessRuleException("لم يتم إرسال رمز تحقق"));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (code.isConsumed() || now.isAfter(code.getExpiresAt())) {
            throw new BusinessRuleException("انتهت صلاحية رمز التحقق، اطلب رمزًا جديدًا");
        }
        if (code.getAttempts() >= properties.registration().maxAttempts()) {
            throw new BusinessRuleException("تم تجاوز عدد المحاولات، اطلب رمزًا جديدًا");
        }
        if (!RegistrationValidation.isSixDigitCode(entered) || !code.getCode().equals(entered)) {
            code.setAttempts(code.getAttempts() + 1);
            codeRepository.save(code);
            throw new BusinessRuleException("رمز التحقق غير صحيح");
        }
        if (consume) {
            code.setConsumed(true);
            codeRepository.save(code);
        }
    }

    /**
     * Creates the student login account: {@code username} is the display name
     * (their full Arabic name) and {@code email} is what they sign in with.
     */
    private User createAccount(String username, String email, String password, UUID adminId) {
        // Display names may repeat (V23) - only the email identifies an account.
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException(EMAIL_TAKEN);
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(Role.STUDENT);
        user.setAdminId(adminId);
        user.setActive(true);
        return userRepository.save(user);
    }

    private LoginResponse autoLogin(User user) {
        String token = jwtService.issue(AuthenticatedUser.from(user));
        return new LoginResponse(token, principalViewFactory.of(user));
    }

    private static void matchPasswords(String password, String confirm) {
        if (!password.equals(confirm)) {
            throw new BusinessRuleException(PASSWORDS_MISMATCH);
        }
    }

    private static String maskPhone(String phone) {
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() <= 4) {
            return "****";
        }
        return "•".repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
    }
}
