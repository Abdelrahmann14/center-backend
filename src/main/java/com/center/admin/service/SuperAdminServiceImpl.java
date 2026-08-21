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

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.admin.dto.CreateAdminRequest;
import com.center.admin.dto.UpdateAdminRequest;
import com.center.admin.dto.AdminSummaryResponse;
import com.center.admin.dto.AssistantAdminResponse;
import com.center.admin.entity.AdminModule;
import com.center.admin.entity.Module;
import com.center.user.entity.User;
import com.center.common.enums.NotificationType;
import com.center.common.enums.Role;
import com.center.common.exception.BusinessRuleException;
import com.center.common.exception.DuplicateResourceException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.admin.repository.AdminModuleRepository;
import com.center.admin.repository.ModuleRepository;
import com.center.admin.repository.SuperAdminRepository;
import com.center.admin.repository.SuperAdminRepository.AdminSummaryRow;
import com.center.user.repository.UserRepository;
import com.center.settings.service.SettingsService;
import com.center.admin.service.SuperAdminService;
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
    private final SettingsService settingsService;
    private final com.center.student.repository.StudentRepository studentRepository;
    private final com.center.whatsapp.repository.WhatsappConfigRepository whatsappConfigRepo;
    private final org.springframework.context.ApplicationEventPublisher events;

    @Override
    @Transactional(readOnly = true)
    public List<AdminSummaryResponse> listAdmins(String q) {
        return superAdminRepository.findAdminSummaries(search(q)).stream().map(this::toResponse).toList();
    }

    /** A blank query means "no filter", not "match the empty string". */
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
        admin.setOfficePhone(normalizePhone(request.officePhone()));
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
        admin.setOfficePhone(normalizePhone(request.officePhone()));
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
                row.getOfficePhone(),
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
