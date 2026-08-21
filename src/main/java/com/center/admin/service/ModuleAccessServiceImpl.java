package com.center.admin.service;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.common.enums.Role;
import com.center.admin.repository.ModuleRepository;
import com.center.user.repository.PermissionRepository;
import com.center.admin.service.ModuleAccessService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ModuleAccessServiceImpl implements ModuleAccessService {

    private final PermissionRepository permissionRepository;
    private final ModuleRepository moduleRepository;

    @Override
    @Transactional(readOnly = true)
    public Set<String> permissionCodes(Role role, UUID adminId, UUID userId) {
        if (role == null) {
            return Set.of();
        }
        return switch (role) {
            case SUPER_ADMIN -> Set.copyOf(permissionRepository.findAllActivePermissionCodes());
            case ADMIN -> adminId == null
                    ? Set.of()
                    : Set.copyOf(permissionRepository.findAdminPermissionCodes(adminId));
            case USER -> (adminId == null || userId == null)
                    ? Set.of()
                    : Set.copyOf(permissionRepository.findUserPermissionCodes(userId, adminId));
        };
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> enabledModuleCodes(Role role, UUID adminId) {
        if (role == Role.SUPER_ADMIN) {
            return Set.copyOf(moduleRepository.findAllActiveModuleCodes());
        }
        if ((role == Role.ADMIN || role == Role.USER) && adminId != null) {
            return Set.copyOf(moduleRepository.findEnabledModuleCodesForAdmin(adminId));
        }
        return Set.of();
    }
}
