package com.center.parent.service;
import com.center.student.entity.Student;

import com.center.parent.dto.ParentForgotSendRequest;
import com.center.parent.dto.ParentForgotVerifyRequest;
import com.center.parent.dto.ParentRegistrationRequest;
import com.center.parent.dto.ParentResetRequest;
import com.center.auth.dto.LoginResponse;
import com.center.parent.dto.ParentCheckResponse;
import com.center.parent.dto.ParentPendingResponse;
import com.center.account.dto.SendCodeResponse;

/** Public parent self-registration and the parent forgot-password flow. */
public interface ParentSignupService {

    /** Confirms a Student Code exists (and has room for another parent) before signup. */
    ParentCheckResponse checkStudent(int serial);

    /**
     * Creates a pending parent account linked to the given student, notifies the
     * student, and returns the pending screen. No auto-login - the account stays
     * inactive until the student approves.
     */
    ParentPendingResponse registerNew(ParentRegistrationRequest request);

    SendCodeResponse sendResetCode(ParentForgotSendRequest request);

    void verifyResetCode(ParentForgotVerifyRequest request);

    LoginResponse resetPassword(ParentResetRequest request);
}
