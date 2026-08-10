package com.center.admin.catalog;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.center.admin.entity.Module;
import com.center.user.entity.Permission;
import com.center.admin.repository.ModuleRepository;
import com.center.user.repository.PermissionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reconciles the DB catalog with {@link ModuleCatalog} on every boot: inserts any
 * module/permission a developer registered in code but that isn't in the DB yet,
 * and refreshes descriptive metadata (names, ownership flags, ordering). Runs
 * after Flyway (which seeds the baseline), so a fresh feature needs only a
 * catalog entry + a restart - no manual migration for the catalog rows.
 *
 * <p>Idempotent and additive: it never deletes rows or flips an admin's existing
 * grants. Unconditional (no {@code @Profile}) so it keeps the catalog current on
 * every normal start.
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class ModuleCatalogSyncRunner implements ApplicationRunner {

    private final ModuleRepository moduleRepository;
    private final PermissionRepository permissionRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, Permission> existingPerms = permissionRepository.findAll().stream()
                .collect(Collectors.toMap(Permission::getCode, Function.identity()));
        int newModules = 0;
        int newPermissions = 0;

        for (ModuleCatalog.ModuleDef def : ModuleCatalog.MODULES) {
            Module module = moduleRepository.findByCode(def.code()).orElse(null);
            boolean isNew = module == null;
            if (isNew) {
                module = new Module();
                module.setCode(def.code());
                newModules++;
            }
            // Refresh descriptive metadata; leave is_active alone so an admin can
            // retire a module in the DB without the code resurrecting it.
            module.setNameAr(def.nameAr());
            module.setDescriptionAr(def.descriptionAr());
            module.setCategory(def.category());
            module.setPlatformControlled(def.platformControlled());
            module.setAdminManaged(def.adminManaged());
            module.setDefaultEnabled(def.defaultEnabled());
            module.setSortOrder(def.sortOrder());
            module = moduleRepository.save(module);
            UUID moduleId = module.getId();

            for (ModuleCatalog.PermissionDef pd : def.permissions()) {
                Permission perm = existingPerms.get(pd.code());
                if (perm == null) {
                    perm = new Permission();
                    perm.setCode(pd.code());
                    newPermissions++;
                }
                perm.setModuleId(moduleId);
                perm.setAction(pd.action());
                perm.setNameAr(pd.nameAr());
                perm.setSortOrder(pd.sortOrder());
                permissionRepository.save(perm);
            }
        }

        if (newModules > 0 || newPermissions > 0) {
            log.info("RBAC catalog synced: {} new module(s), {} new permission(s)", newModules, newPermissions);
        }
    }
}
