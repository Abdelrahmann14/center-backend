package com.center.user.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.user.dto.CreateUserRequest;
import com.center.user.dto.UpdateUserRequest;
import com.center.user.dto.AssistantResponse;
import com.center.user.dto.UserResponse;
import com.center.user.entity.User;
import com.center.common.enums.Role;
import com.center.common.exception.BusinessRuleException;
import com.center.common.exception.DuplicateResourceException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.finance.repository.LessonAttendanceRepository;
import com.center.user.mapper.UserMapper;
import com.center.user.repository.UserPermissionRepository;
import com.center.user.repository.UserRepository;
import com.center.user.service.UserService;
import com.center.common.tenant.TenantContext;
import com.center.common.util.TextUtils;
import com.center.common.validation.EmailPolicy;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String NOT_FOUND = "المساعد غير موجود";
    private static final String NAME_TAKEN = "اسم المساعد مستخدم بالفعل";
    private static final String EMAIL_TAKEN = "هذا البريد الإلكتروني مستخدم بالفعل";

    private final UserRepository userRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final LessonAttendanceRepository lessonAttendanceRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        UUID admin = currentAdmin();
        // One grants query for the whole workspace, then grouped in memory - the
        // table shows every assistant's permissions without a query per row.
        Map<UUID, List<String>> granted = userPermissionRepository.findGrantedNames(admin).stream()
                .collect(Collectors.groupingBy(
                        UserPermissionRepository.GrantedNameRow::getUserId,
                        LinkedHashMap::new,
                        Collectors.mapping(UserPermissionRepository.GrantedNameRow::getNameAr, Collectors.toList())));

        // One count query for the whole workspace, joined in memory - the table
        // shows each assistant's attendance without a query per row.
        Map<UUID, Long> attendance = lessonAttendanceRepository.countByUser().stream()
                .collect(Collectors.toMap(
                        LessonAttendanceRepository.UserAttendanceCount::getUserId,
                        LessonAttendanceRepository.UserAttendanceCount::getCount));

        return userRepository.findByAdminIdOrderByCreatedAtAsc(admin).stream()
                .map(u -> toResponse(u, granted.getOrDefault(u.getId(), List.of()),
                        attendance.getOrDefault(u.getId(), 0L)))
                .toList();
    }

    private static UserResponse toResponse(User user, List<String> permissions, long attendanceCount) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getPhone(),
                user.getRole(), user.getCreatedAt(), permissions, attendanceCount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssistantResponse> findAssistants() {
        return userMapper.toAssistantResponses(
                userRepository.findByRoleAndAdminIdOrderByUsername(Role.USER, currentAdmin()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(String username) {
        return username != null
                && !username.isBlank()
                && !userRepository.existsByUsername(username.strip());
    }

    @Override
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String username = request.username().strip();
        // The display name stays unique so two assistants are never confused.
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException(NAME_TAKEN);
        }
        String email = EmailPolicy.build(request.email(), Role.USER);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException(EMAIL_TAKEN);
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPhone(TextUtils.blankToNull(request.phone()));
        user.setRole(Role.USER);
        // The new assistant belongs to the workspace that created it.
        user.setAdminId(currentAdmin());
        return toResponse(userRepository.save(user), List.of(), 0L);
    }

    @Override
    @Transactional
    public UserResponse update(UUID userId, UpdateUserRequest request) {
        User user = findEntity(userId);
        String username = request.username().strip();
        if (userRepository.existsByUsernameAndIdNot(username, userId)) {
            throw new DuplicateResourceException(NAME_TAKEN);
        }
        String email = EmailPolicy.build(request.email(), user.getRole());
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, userId)) {
            throw new DuplicateResourceException(EMAIL_TAKEN);
        }
        user.setUsername(username);
        user.setEmail(email);
        user.setPhone(TextUtils.blankToNull(request.phone()));
        // A blank password means "leave the current one alone".
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return toResponse(userRepository.save(user), List.of(), 0L);
    }

    @Override
    @Transactional
    public void delete(UUID userId) {
        User user = findEntity(userId);
        // The admin account can never be deleted - by anyone.
        if (user.getRole() == Role.ADMIN) {
            throw new BusinessRuleException("لا يمكن حذف حساب المدرّس");
        }
        userRepository.delete(user);
    }

    /** An assistant is only visible/editable within its own workspace. */
    private User findEntity(UUID userId) {
        return userRepository.findByIdAndAdminId(userId, currentAdmin())
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    /**
     * The workspace acting on this request. Never null on an assistant-management
     * path: an admin is bound to its own id and an assistant to its owning admin.
     */
    private static UUID currentAdmin() {
        return TenantContext.get();
    }
}
