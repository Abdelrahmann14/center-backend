package com.center.admin.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.center.notification.dto.BroadcastRequest;
import com.center.admin.dto.CreateAdminRequest;
import com.center.admin.dto.SuperParentUpdateRequest;
import com.center.admin.dto.SuperStudentUpdateRequest;
import com.center.admin.dto.UpdateAdminRequest;
import com.center.admin.dto.AdminSummaryResponse;
import com.center.admin.dto.AssistantAdminResponse;
import com.center.notification.dto.BroadcastResult;
import com.center.notification.dto.OutgoingMessageResponse;
import com.center.admin.dto.ParentAdminResponse;
import com.center.admin.dto.ParentDetailResponse;
import com.center.admin.dto.StudentAdminResponse;
import com.center.admin.dto.StudentDetailResponse;
import com.center.user.dto.UserSearchResponse;

/** Platform administration, reserved for the super admin. */
public interface SuperAdminService {

    /** Every Admin, optionally filtered by a case-insensitive name query. */
    List<AdminSummaryResponse> listAdmins(String q);

    /**
     * Students across all workspaces, paginated. {@code q} matches name / serial /
     * phone; the remaining arguments are optional equality filters (null = ignore).
     */
    Page<StudentAdminResponse> listStudents(String q, UUID teacherId, String grade, String gender,
            Boolean registered, Boolean active, Pageable pageable);

    /** Distinct grades across every workspace, for the students filter. */
    List<String> listStudentGrades();

    /** Every parent across all workspaces, name-filtered, paginated. */
    Page<ParentAdminResponse> listParents(String q, Pageable pageable);

    /** Edit a student's core fields. */
    void updateStudent(UUID studentId, SuperStudentUpdateRequest request);

    /** Permanently delete a student and their child rows. */
    void deleteStudent(UUID studentId);

    /** Edit a parent's core fields. */
    void updateParent(UUID parentId, SuperParentUpdateRequest request);

    /** Permanently delete a parent, their links, and their login account. */
    void deleteParent(UUID parentId);

    /** One teacher's assistants (login accounts). */
    List<AssistantAdminResponse> listAssistants(UUID adminId);

    /** Full student profile (cross-tenant). */
    StudentDetailResponse getStudentDetail(UUID studentId);

    /** Full parent profile (cross-tenant). */
    ParentDetailResponse getParentDetail(UUID parentId);

    /** Enable or disable a student. */
    void setStudentActive(UUID studentId, boolean active);

    /** Enable or disable a parent (their login account). */
    void setParentActive(UUID parentId, boolean active);

    AdminSummaryResponse getAdmin(UUID adminId);

    /** Enable or disable Google Contacts sync for one Admin (super-admin only). */

    /** Enable or disable the WhatsApp numbers feature for one Admin (super-admin only). */
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

    /** Send a broadcast to the union of the selected recipient facets. */
    BroadcastResult broadcast(BroadcastRequest request);

    /** Recent super-admin broadcasts, newest first (History panel). */
    List<OutgoingMessageResponse> listOutgoing();

    /** Delete a sent broadcast and remove it from every recipient's inbox. */
    void deleteOutgoing(UUID outgoingId);

    /** Name search across all accounts, for the notification picker. */
    List<UserSearchResponse> searchUsers(String q);
}
