package com.center.account.service;

import java.util.Arrays;
import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.account.dto.ChangePasswordRequest;
import com.center.account.dto.ChangePhoneRequest;
import com.center.account.dto.AccountResponse;
import com.center.parent.entity.Parent;
import com.center.student.entity.Student;
import com.center.user.entity.User;
import com.center.common.enums.LinkStatus;
import com.center.common.enums.Role;
import com.center.common.exception.BusinessRuleException;
import com.center.common.exception.DuplicateResourceException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.parent.repository.ParentRepository;
import com.center.parent.repository.ParentStudentLinkRepository;
import com.center.student.repository.StudentRepository;
import com.center.user.repository.UserRepository;
import com.center.auth.security.AuthenticatedUser;
import com.center.account.service.AccountService;
import com.center.common.tenant.TenantContext;
import com.center.common.tenant.TenantScopedExecutor;
import com.center.registration.validation.RegistrationValidation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountServiceImpl implements AccountService {

    private static final String NOT_A_STUDENT = "هذا الحساب ليس حساب طالب";
    private static final String NOT_A_PARENT = "هذا الحساب ليس حساب ولي أمر";
    private static final String WRONG_CURRENT = "كلمة المرور الحالية غير صحيحة";
    private static final String PASSWORDS_MISMATCH = "كلمتا المرور غير متطابقتين";
    private static final String PHONE_TAKEN = "رقم الهاتف مسجّل بالفعل لطالب آخر";
    private static final String NO_PHONE = "لا يمكن تغيير رقم الهاتف لهذا الحساب";

    private final UserRepository userRepository;
    private final ParentRepository parentRepository;
    private final StudentRepository studentRepository;
    private final ParentStudentLinkRepository linkRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantScopedExecutor tx;

    @Override
    @Transactional(readOnly = true)
    public AccountResponse get(AuthenticatedUser principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("الحساب غير موجود"));

        Integer code = null;
        String phone = null;
        switch (user.getRole()) {
            case PARENT -> {
                Parent parent = parentRepository.findByUserId(user.getId()).orElse(null);
                if (parent != null) {
                    code = parent.getSerial();
                    phone = parent.getPhone();
                }
            }
            case STUDENT -> {
                // The student reads their own record within their bound tenant.
                Student student = studentRepository.findByUserId(user.getId()).orElse(null);
                if (student != null) {
                    code = student.getSerial();
                    phone = firstPhone(student.getStudentPhones());
                }
            }
            default -> {
                // admin / assistant / super admin have no code or editable phone here
            }
        }
        return new AccountResponse(user.getId(), user.getUsername(), user.getEmail(),
                user.getRole(), code, phone);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request, AuthenticatedUser principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("الحساب غير موجود"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException(WRONG_CURRENT);
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessRuleException(PASSWORDS_MISMATCH);
        }
        RegistrationValidation.requireStrongPassword(request.newPassword());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        log.info("Account {} changed password", user.getId());
    }

    @Override
    public void changePhone(ChangePhoneRequest request, AuthenticatedUser principal) {
        // Not @Transactional: a parent syncs students across workspaces, which
        // requires callAs + a fresh tenant-bound session per student.
        Role role = principal.getRole();
        String owner = role == Role.PARENT ? "ولي الأمر" : "الطالب";
        String phone = RegistrationValidation.requirePhone(request.phone(), owner);

        switch (role) {
            case PARENT -> changeParentPhone(principal.getId(), phone);
            case STUDENT -> changeStudentPhone(principal, phone);
            default -> throw new BusinessRuleException(NO_PHONE);
        }
    }

    private void changeParentPhone(UUID userId, String phone) {
        Parent parent = parentRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessRuleException(NOT_A_PARENT));

        tx.inTenantTx(() -> {
            Parent managed = parentRepository.findById(parent.getId()).orElseThrow();
            managed.setPhone(phone);
            parentRepository.save(managed);
            return null;
        });

        // Re-sync the trusted number onto every linked student, each in its own
        // workspace.
        linkRepository.findByParentIdAndStatus(parent.getId(), LinkStatus.APPROVED)
                .forEach(link -> TenantContext.callAs(link.getStudentAdminId(), () -> tx.inTenantTx(() -> {
                    studentRepository.findById(link.getStudentId()).ifPresent(student -> {
                        student.setParentPhones(new String[] {phone});
                        studentRepository.save(student);
                    });
                    return null;
                })));
        log.info("Parent {} changed phone and re-synced linked students", parent.getId());
    }

    private void changeStudentPhone(AuthenticatedUser principal, String phone) {
        TenantContext.callAs(principal.getAdminId(), () -> tx.inTenantTx(() -> {
            Student student = studentRepository.findByUserId(principal.getId())
                    .orElseThrow(() -> new BusinessRuleException(NOT_A_STUDENT));
            boolean alreadyMine = student.getStudentPhones() != null
                    && Arrays.asList(student.getStudentPhones()).contains(phone);
            if (!alreadyMine && studentRepository.studentPhoneExistsAnywhere(phone)) {
                throw new DuplicateResourceException(PHONE_TAKEN);
            }
            student.setStudentPhones(new String[] {phone});
            studentRepository.save(student);
            return null;
        }));
        log.info("Student {} changed phone", principal.getId());
    }

    private static String firstPhone(String[] phones) {
        return phones == null || phones.length == 0 ? null : phones[0];
    }
}
