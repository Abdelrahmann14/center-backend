package com.center.user.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.user.dto.PermissionActionResponse;
import com.center.user.dto.PermissionModuleResponse;
import com.center.admin.entity.Module;
import com.center.user.entity.Permission;
import com.center.user.entity.User;
import com.center.user.entity.UserPermission;
import com.center.common.enums.Role;
import com.center.common.exception.BusinessRuleException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.admin.repository.ModuleRepository;
import com.center.user.repository.PermissionRepository;
import com.center.user.repository.UserPermissionRepository;
import com.center.user.repository.UserRepository;
import com.center.auth.security.PermissionCache;
import com.center.user.service.PermissionService;
import com.center.common.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private static final String NOT_FOUND = "المساعد غير موجود";
    private static final String NOT_ASSISTANT = "لا يمكن تعيين صلاحيات لهذا الحساب";
    private static final String FORBIDDEN = "لا يمكن منح صلاحية لا تملكها";

    private final ModuleRepository moduleRepository;
    private final PermissionRepository permissionRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final UserRepository userRepository;
    private final PermissionCache permissionCache;

    @Override
    @Transactional(readOnly = true)
    public List<PermissionModuleResponse> catalog() {
        UUID admin = currentAdmin();
        Set<String> enabled = new HashSet<>(moduleRepository.findEnabledModuleCodesForAdmin(admin));
        List<PermissionModuleResponse> out = new ArrayList<>();
        for (Module module : moduleRepository.findByActiveTrueOrderBySortOrder()) {
            if (!module.isAdminManaged() || !enabled.contains(module.getCode())) {
                continue;
            }
            List<PermissionActionResponse> actions = permissionRepository
                    .findByModuleIdOrderBySortOrder(module.getId()).stream()
                    .map(p -> new PermissionActionResponse(p.getId(), p.getCode(), p.getAction(), p.getNameAr()))
                    .toList();
            if (actions.isEmpty()) {
                continue;
            }
            out.add(new PermissionModuleResponse(
                    module.getCode(), module.getNameAr(), module.getDescriptionAr(), module.getCategory(), actions));
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> userPermissions(UUID userId) {
        requireAssistant(userId);
        List<UUID> permissionIds = userPermissionRepository.findByUserId(userId).stream()
                .map(UserPermission::getPermissionId)
                .toList();
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return permissionRepository.findAllById(permissionIds).stream()
                .map(Permission::getCode)
                .sorted()
                .toList();
    }

    @Override
    @Transactional
    public void setUserPermissions(UUID userId, List<String> codes) {
        UUID admin = currentAdmin();
        requireAssistant(userId);

        List<String> requested = codes == null ? List.of()
                : codes.stream().filter(Objects::nonNull).map(String::strip).distinct().toList();

        if (!requested.isEmpty()) {
            // An admin may grant only permissions they themselves hold (a module
            // that is admin-managed AND currently enabled for them).
            Set<String> allowed = new HashSet<>(permissionRepository.findAdminManagedPermissionCodes(admin));
            for (String code : requested) {
                if (!allowed.contains(code)) {
                    throw new BusinessRuleException(FORBIDDEN);
                }
            }
        }

        List<Permission> permissions = requested.isEmpty()
                ? List.of()
                : permissionRepository.findByCodeIn(requested);

        // Replace the set: clear then re-insert. Flush the delete first so the
        // unique (user_id, permission_id) constraint never trips mid-transaction.
        userPermissionRepository.deleteByUserId(userId);
        userPermissionRepository.flush();
        for (Permission permission : permissions) {
            UserPermission grant = new UserPermission();
            grant.setUserId(userId);
            grant.setAdminId(admin);
            grant.setPermissionId(permission.getId());
            grant.setGrantedBy(admin);
            userPermissionRepository.save(grant);
        }
        permissionCache.evict(userId);
    }

    /** The target must be an assistant inside the current admin's workspace. */
    private User requireAssistant(UUID userId) {
        User user = userRepository.findByIdAndAdminId(userId, currentAdmin())
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        if (user.getRole() != Role.USER) {
            throw new BusinessRuleException(NOT_ASSISTANT);
        }
        return user;
    }

    private static UUID currentAdmin() {
        return TenantContext.get();
    }
}
