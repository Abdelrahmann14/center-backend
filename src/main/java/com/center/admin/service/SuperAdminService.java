package com.center.admin.service;

import java.util.List;
import java.util.UUID;

import com.center.admin.dto.AdminSummaryResponse;
import com.center.admin.dto.AssistantAdminResponse;
import com.center.admin.dto.CreateAdminRequest;
import com.center.admin.dto.UpdateAdminRequest;

/**
 * Platform administration, reserved for the super admin: the teachers, their
 * assistants, and what each workspace is allowed to use.
 *
 * <p>It never reaches into a workspace's own data. It used to also administer
 * student and guardian login accounts and send platform-wide broadcasts; both
 * went with the accounts they existed for.
 */
public interface SuperAdminService {

    /** Every Admin, optionally filtered by a case-insensitive name query. */
    List<AdminSummaryResponse> listAdmins(String q);

    /** One teacher's assistants (login accounts). */
    List<AssistantAdminResponse> listAssistants(UUID adminId);

    AdminSummaryResponse getAdmin(UUID adminId);

    /** Enable or disable the WhatsApp numbers feature for one Admin. */
    void setWhatsappSync(UUID adminId, boolean enabled);

    AdminSummaryResponse createAdmin(CreateAdminRequest request);

    /** Rename and/or reset the Admin's password. */
    AdminSummaryResponse updateAdmin(UUID adminId, UpdateAdminRequest request);

    /** Enable or disable an Admin (and, transitively, its whole workspace). */
    void setActive(UUID adminId, boolean active);

    /** Permanently delete an Admin and every row in its workspace. */
    void deleteAdmin(UUID adminId);

    /** Set any user's profile photo from a base64 data URL. */
    void setUserPhoto(UUID userId, String dataUrl);

    /** Remove any user's profile photo. */
    void clearUserPhoto(UUID userId);
}
