package com.center.parent.service;
import com.center.parent.entity.Parent;

import java.util.List;
import java.util.UUID;

import com.center.parent.dto.LinkedParentResponse;
import com.center.parent.dto.LinkedStudentResponse;
import com.center.parent.dto.ParentPendingResponse;
import com.center.parent.dto.ParentRequestResponse;
import com.center.auth.security.AuthenticatedUser;

/** Parent<->student linking: the student's approvals and the parent's children. */
public interface ParentLinkService {

    /** The pending link requests the student must decide on (Settings -> Parents). */
    List<ParentRequestResponse> pendingRequests(AuthenticatedUser student);

    /** The guardians already linked to the student. */
    List<LinkedParentResponse> linkedParents(AuthenticatedUser student);

    /** Approves a request: links the accounts, activates the parent, syncs the phone. */
    void approve(UUID linkId, AuthenticatedUser student);

    /** Rejects a request. */
    void reject(UUID linkId, AuthenticatedUser student);

    /** A logged-in parent requests a link to another student (in-app result only). */
    ParentPendingResponse addStudent(int serial, AuthenticatedUser parent);

    /** The students already linked to the parent. */
    List<LinkedStudentResponse> linkedStudents(AuthenticatedUser parent);
}
