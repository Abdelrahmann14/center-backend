package com.center.parent.service;
import com.center.student.service.StudentSignupServiceImpl;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.center.common.config.ApplicationProperties;
import com.center.parent.dto.ParentForgotSendRequest;
import com.center.parent.dto.ParentForgotVerifyRequest;
import com.center.parent.dto.ParentRegistrationRequest;
import com.center.parent.dto.ParentResetRequest;
import com.center.auth.dto.LoginResponse;
import com.center.parent.dto.ParentCheckResponse;
import com.center.parent.dto.ParentPendingResponse;
import com.center.account.dto.SendCodeResponse;
import com.center.parent.entity.Parent;
import com.center.parent.entity.ParentStudentLink;
import com.center.parent.entity.ParentVerificationCode;
import com.center.student.entity.Student;
import com.center.user.entity.User;
import com.center.common.enums.LinkStatus;
import com.center.common.enums.NotificationType;
import com.center.common.enums.Role;
import com.center.common.exception.BusinessRuleException;
import com.center.common.exception.DuplicateResourceException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.auth.service.PrincipalViewFactory;
import com.center.parent.repository.ParentRepository;
import com.center.parent.repository.ParentStudentLinkRepository;
import com.center.parent.repository.ParentVerificationCodeRepository;
import com.center.student.repository.StudentRepository;
import com.center.student.repository.StudentRepository.StudentIdentity;
import com.center.user.repository.UserRepository;
import com.center.auth.security.AuthenticatedUser;
import com.center.auth.security.JwtService;
import com.center.whatsapp.service.GreenApiClient;
import com.center.notification.service.MessageTemplateService;
import com.center.notification.service.NotificationService;
import com.center.parent.service.ParentSignupService;
import com.center.settings.service.SettingsService;
import com.center.common.tenant.TenantContext;
import com.center.common.tenant.TenantScopedExecutor;
import com.center.common.validation.EmailPolicy;
import com.center.registration.validation.RegistrationValidation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Public parent self-registration and the parent forgot-password flow.
 *
 * <p>Like {@link StudentSignupServiceImpl}, these run with no JWT and therefore
 * no bound tenant. Reading a {@code @TenantId} student binds its workspace with
 * {@link TenantContext#callAs} inside {@link TenantScopedExecutor#inTenantTx}; the
 * parent's own rows (users, parents, links, notifications) are non-tenant and are
 * written in the same fresh transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ParentSignupServiceImpl implements ParentSignupService {

    /** A student may be linked with at most this many parents. */
    private static final int MAX_PARENTS_PER_STUDENT = 2;

    private static final String STUDENT_NOT_FOUND = "لا يوجد طالب بهذا الكود";
    private static final String STUDENT_NO_ACCOUNT = "لم يُنشئ هذا الطالب حسابًا بعد، لا يمكن ربطه";
    private static final String PARENT_LIMIT = "هذا الطالب مرتبط بالفعل بالحد الأقصى من أولياء الأمور";
    private static final String PASSWORDS_MISMATCH = "كلمتا المرور غير متطابقتين";
    private static final String EMAIL_TAKEN = "هذا البريد الإلكتروني مستخدم بالفعل، اختر اسمًا آخر";
    private static final String PARENT_NOT_FOUND = "لا يوجد ولي أمر بهذا الكود";
    private static final String PARENT_INACTIVE = "لم يتم تفعيل هذا الحساب بعد";

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final ParentStudentLinkRepository linkRepository;
    private final ParentVerificationCodeRepository codeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PrincipalViewFactory principalViewFactory;
    private final GreenApiClient greenApiClient;
    private final NotificationService notificationService;
    private final MessageTemplateService templateService;
    private final SettingsService settingsService;
    private final ApplicationProperties properties;
    private final TenantScopedExecutor tx;

    private final SecureRandom random = new SecureRandom();

    // --- Check ------------------------------------------------------------

    @Override
    public ParentCheckResponse checkStudent(int serial) {
        StudentIdentity identity = studentRepository.findIdentityBySerial(serial)
                .orElseThrow(() -> new ResourceNotFoundException(STUDENT_NOT_FOUND));

        return TenantContext.callAs(identity.getAdminId(), () -> tx.inTenantTx(() -> {
            Student student = studentRepository.findById(identity.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(STUDENT_NOT_FOUND));
            if (student.getUserId() == null) {
                throw new BusinessRuleException(STUDENT_NO_ACCOUNT);
            }
            long approved = linkRepository.countByStudentIdAndStatus(student.getId(), LinkStatus.APPROVED);
            return new ParentCheckResponse(student.getName(), approved < MAX_PARENTS_PER_STUDENT);
        }));
    }

    // --- Register ---------------------------------------------------------

    @Override
    public ParentPendingResponse registerNew(ParentRegistrationRequest request) {
        String name = RegistrationValidation.requireThreePartArabicName(request.fullName());
        String email = EmailPolicy.build(request.username(), Role.PARENT);
        matchPasswords(request.password(), request.confirmPassword());
        RegistrationValidation.requireStrongPassword(request.password());
        String phone = RegistrationValidation.requirePhone(request.phone(), "ولي الأمر");

        StudentIdentity identity = studentRepository.findIdentityBySerial(request.serial())
                .orElseThrow(() -> new ResourceNotFoundException(STUDENT_NOT_FOUND));

        return TenantContext.callAs(identity.getAdminId(), () -> tx.inTenantTx(() -> {
            Student student = studentRepository.findById(identity.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(STUDENT_NOT_FOUND));
            if (student.getUserId() == null) {
                throw new BusinessRuleException(STUDENT_NO_ACCOUNT);
            }
            if (linkRepository.countByStudentIdAndStatus(student.getId(), LinkStatus.APPROVED)
                    >= MAX_PARENTS_PER_STUDENT) {
                throw new BusinessRuleException(PARENT_LIMIT);
            }

            // The parent login account is created INACTIVE - it cannot sign in
            // until the student approves the link.
            User account = createParentAccount(name, email, request.password());

            Parent parent = new Parent();
            parent.setName(name);
            parent.setPhone(phone);
            parent.setUserId(account.getId());
            parentRepository.save(parent);

            ParentStudentLink link = newPendingLink(parent.getId(), student.getId(),
                    identity.getAdminId(), phone);
            linkRepository.save(link);

            MessageTemplateService.Rendered msg = templateService.render("parent_link_request",
                    Map.of("name", name));
            notificationService.notify(student.getUserId(), settingsService.senderName(),
                    NotificationType.PARENT_LINK_REQUEST, msg.title(), msg.body(), link.getId());

            log.info("Parent '{}' requested link to student serial {}", name, request.serial());
            return new ParentPendingResponse(student.getName());
        }));
    }

    // --- Forgot password --------------------------------------------------

    @Override
    public SendCodeResponse sendResetCode(ParentForgotSendRequest request) {
        Parent parent = parentRepository.findBySerial(request.parentCode())
                .orElseThrow(() -> new ResourceNotFoundException(PARENT_NOT_FOUND));

        return tx.inTenantTx(() -> {
            requireActiveAccount(parent.getUserId());

            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            long recent = codeRepository.countByParentIdAndCreatedAtAfter(parent.getId(), now.minusHours(1));
            if (recent >= properties.registration().maxSendsPerHour()) {
                throw new BusinessRuleException("لقد تجاوزت عدد محاولات الإرسال، حاول لاحقًا");
            }

            int ttlMinutes = properties.registration().codeTtlMinutes();
            String code = String.format("%06d", random.nextInt(1_000_000));

            ParentVerificationCode row = new ParentVerificationCode();
            row.setParentId(parent.getId());
            row.setCode(code);
            row.setExpiresAt(now.plusMinutes(ttlMinutes));
            row.setCreatedAt(now);
            codeRepository.save(row);

            greenApiClient.sendText("parent_password_reset", parent.getPhone(), templateService.render("parent_password_reset",
                    Map.of("code", code, "minutes", String.valueOf(ttlMinutes))).body());
            log.info("Password-reset code issued for parent code {}", request.parentCode());

            return new SendCodeResponse(maskPhone(parent.getPhone()), ttlMinutes * 60);
        });
    }

    @Override
    public void verifyResetCode(ParentForgotVerifyRequest request) {
        Parent parent = parentRepository.findBySerial(request.parentCode())
                .orElseThrow(() -> new ResourceNotFoundException(PARENT_NOT_FOUND));
        tx.inTenantTx(() -> {
            validateCode(parent.getId(), request.code(), false);
            return null;
        });
    }

    @Override
    public LoginResponse resetPassword(ParentResetRequest request) {
        matchPasswords(request.password(), request.confirmPassword());
        RegistrationValidation.requireStrongPassword(request.password());

        Parent parent = parentRepository.findBySerial(request.parentCode())
                .orElseThrow(() -> new ResourceNotFoundException(PARENT_NOT_FOUND));

        return tx.inTenantTx(() -> {
            User account = requireActiveAccount(parent.getUserId());
            validateCode(parent.getId(), request.code(), true);
            account.setPasswordHash(passwordEncoder.encode(request.password()));
            userRepository.save(account);
            log.info("Parent code {} reset password", request.parentCode());
            return autoLogin(account);
        });
    }

    // --- Helpers ----------------------------------------------------------

    private User requireActiveAccount(UUID userId) {
        User account = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(PARENT_NOT_FOUND));
        if (!account.isActive()) {
            throw new BusinessRuleException(PARENT_INACTIVE);
        }
        return account;
    }

    private User createParentAccount(String name, String email, String password) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException(EMAIL_TAKEN);
        }
        User user = new User();
        user.setUsername(name);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(Role.PARENT);
        user.setAdminId(null); // a parent owns no workspace, like a root account
        user.setActive(false); // pending until a student approves
        return userRepository.save(user);
    }

    private static ParentStudentLink newPendingLink(UUID parentId, UUID studentId,
            UUID studentAdminId, String phone) {
        ParentStudentLink link = new ParentStudentLink();
        link.setParentId(parentId);
        link.setStudentId(studentId);
        link.setStudentAdminId(studentAdminId);
        link.setStatus(LinkStatus.PENDING);
        link.setPhoneAtRequest(phone);
        return link;
    }

    private void validateCode(UUID parentId, String entered, boolean consume) {
        ParentVerificationCode code = codeRepository
                .findFirstByParentIdOrderByCreatedAtDesc(parentId)
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
