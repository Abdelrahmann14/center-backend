package com.center.admin.service;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.admin.dto.AdminModuleResponse;
import com.center.admin.entity.AdminModule;
import com.center.admin.entity.Module;
import com.center.common.enums.Role;
import com.center.common.exception.BusinessRuleException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.admin.repository.AdminModuleRepository;
import com.center.admin.repository.ModuleRepository;
import com.center.user.repository.UserRepository;
import com.center.auth.security.PermissionCache;
import com.center.admin.service.SuperAdminModuleService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SuperAdminModuleServiceImpl implements SuperAdminModuleService {

    private static final String ADMIN_NOT_FOUND = "المدرّس غير موجود";
    private static final String MODULE_NOT_FOUND = "الوحدة غير موجودة";
    private static final String NOT_PLATFORM = "هذه الوحدة غير خاضعة لتحكم المنصة";

    private final ModuleRepository moduleRepository;
    private final AdminModuleRepository adminModuleRepository;
    private final UserRepository userRepository;
    private final PermissionCache permissionCache;

    @Override
    @Transactional(readOnly = true)
    public List<AdminModuleResponse> listForAdmin(UUID adminId) {
        requireAdmin(adminId);
        Map<UUID, Boolean> overrides = adminModuleRepository.findByAdminId(adminId).stream()
                .collect(Collectors.toMap(AdminModule::getModuleId, AdminModule::isEnabled));
        return moduleRepository.findByActiveTrueOrderBySortOrder().stream()
                .filter(Module::isPlatformControlled)
                .map(m -> new AdminModuleResponse(
                        m.getCode(), m.getNameAr(), m.getDescriptionAr(), m.getCategory(),
                        overrides.getOrDefault(m.getId(), m.isDefaultEnabled())))
                .toList();
    }

    @Override
    @Transactional
    public void setEnabled(UUID adminId, String moduleCode, boolean enabled) {
        requireAdmin(adminId);
        Module module = moduleRepository.findByCode(moduleCode)
                .orElseThrow(() -> new ResourceNotFoundException(MODULE_NOT_FOUND));
        // Only platform-controlled modules are the super admin's to gate.
        if (!module.isPlatformControlled()) {
            throw new BusinessRuleException(NOT_PLATFORM);
        }
        AdminModule row = adminModuleRepository.findByAdminIdAndModuleId(adminId, module.getId())
                .orElseGet(() -> {
                    AdminModule created = new AdminModule();
                    created.setAdminId(adminId);
                    created.setModuleId(module.getId());
                    return created;
                });
        row.setEnabled(enabled);
        adminModuleRepository.save(row);
        // The change affects the admin and every user under them.
        permissionCache.evictWorkspace(adminId);
    }

    private void requireAdmin(UUID adminId) {
        userRepository.findById(adminId)
                .filter(user -> user.getRole() == Role.ADMIN)
                .orElseThrow(() -> new ResourceNotFoundException(ADMIN_NOT_FOUND));
    }
}
