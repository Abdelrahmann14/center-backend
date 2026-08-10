package com.center.student.service;

import java.util.List;
import java.util.UUID;

import com.center.student.dto.ClaimExistingRequest;
import com.center.student.dto.ForgotVerifyRequest;
import com.center.account.dto.SendCodeRequest;
import com.center.student.dto.StudentRegistrationRequest;
import com.center.student.dto.VerifyExistingRequest;
import com.center.group.dto.GroupOptionResponse;
import com.center.auth.dto.LoginResponse;
import com.center.account.dto.SendCodeResponse;
import com.center.student.dto.TeacherOptionResponse;

/**
 * Public student self-registration. Students are the only accounts that create
 * themselves; admins, assistants and super admins are created internally.
 */
public interface StudentSignupService {

    List<TeacherOptionResponse> teachers();

    List<String> grades(UUID adminId);

    List<GroupOptionResponse> groups(UUID adminId, String grade);

    /** Option 1: send a WhatsApp code to an existing student's stored phone. */
    SendCodeResponse sendCode(SendCodeRequest request);

    /** Option 1: verify the code and finish claiming the existing student. Auto-logs in. */
    LoginResponse verifyExisting(ClaimExistingRequest request);

    /** Option 2: create a brand-new student and their account. Auto-logs in. */
    LoginResponse registerNew(StudentRegistrationRequest request);

    /** Forgot password: send a WhatsApp code to the student's stored phone (account must exist). */
    SendCodeResponse sendResetCode(SendCodeRequest request);

    /** Forgot password: check the code without consuming it, gating the reset step. */
    void verifyResetCode(ForgotVerifyRequest request);

    /** Forgot password: consume the code, set the new password, and auto-log in. */
    LoginResponse resetPassword(VerifyExistingRequest request);
}
