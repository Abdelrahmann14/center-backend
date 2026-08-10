package com.center.admin.service;

import java.util.List;
import java.util.UUID;

import com.center.admin.dto.AdminModuleResponse;

/** Level-1 control: the super admin enabling/disabling platform modules per admin. */
public interface SuperAdminModuleService {

    /** The platform modules and their resolved enabled state for one admin. */
    List<AdminModuleResponse> listForAdmin(UUID adminId);

    /** Enable or disable one platform module for one admin. */
    void setEnabled(UUID adminId, String moduleCode, boolean enabled);
}
