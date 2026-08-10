package com.center.student.controller;
import com.center.parent.entity.Parent;
import com.center.registration.entity.Registration;
import com.center.student.entity.Student;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.center.student.dto.ClaimExistingRequest;
import com.center.student.dto.ForgotVerifyRequest;
import com.center.parent.dto.ParentForgotSendRequest;
import com.center.parent.dto.ParentForgotVerifyRequest;
import com.center.parent.dto.ParentRegistrationRequest;
import com.center.parent.dto.ParentResetRequest;
import com.center.account.dto.SendCodeRequest;
import com.center.student.dto.StudentRegistrationRequest;
import com.center.student.dto.VerifyExistingRequest;
import com.center.account.dto.EmailAvailabilityResponse;
import com.center.group.dto.GroupOptionResponse;
import com.center.auth.dto.LoginResponse;
import com.center.parent.dto.ParentCheckResponse;
import com.center.parent.dto.ParentPendingResponse;
import com.center.account.dto.SendCodeResponse;
import com.center.student.dto.TeacherOptionResponse;
import com.center.whatsapp.dto.WhatsappCheckResponse;
import com.center.common.enums.Role;
import com.center.account.service.AccountEmailService;
import com.center.whatsapp.service.GreenApiClient;
import com.center.parent.service.ParentSignupService;
import com.center.student.service.StudentSignupService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Public student self-registration. Unauthenticated by design - students have no
 * account yet. Every write validates its inputs and scopes to the chosen teacher
 * server-side, never trusting caller-supplied tenant headers.
 */
@RestController
@RequestMapping("/api/register")
@RequiredArgsConstructor
@Tag(name = "Student Registration")
public class SignupController {

    private final StudentSignupService signupService;
    private final ParentSignupService parentSignupService;
    private final AccountEmailService accountEmailService;
    private final GreenApiClient greenApiClient;

    @GetMapping("/check-whatsapp")
    @Operation(summary = "Whether a phone number is registered on WhatsApp")
    public WhatsappCheckResponse checkWhatsapp(@RequestParam("phone") String phone) {
        GreenApiClient.WhatsappCheck c = greenApiClient.checkWhatsapp(phone);
        return new WhatsappCheckResponse(c.existsWhatsapp(), c.checked());
    }

    @GetMapping("/teachers")
    @Operation(summary = "Teachers (Admins) a student may register with")
    public List<TeacherOptionResponse> teachers() {
        return signupService.teachers();
    }

    @GetMapping("/teachers/{adminId}/grades")
    @Operation(summary = "A teacher's grades")
    public List<String> grades(@PathVariable UUID adminId) {
        return signupService.grades(adminId);
    }

    @GetMapping("/teachers/{adminId}/groups")
    @Operation(summary = "A teacher's groups for a grade")
    public List<GroupOptionResponse> groups(@PathVariable UUID adminId,
            @RequestParam("grade") String grade) {
        return signupService.groups(adminId, grade);
    }

    @PostMapping("/send-code")
    @Operation(summary = "Send a WhatsApp verification code to an existing student")
    public SendCodeResponse sendCode(@Valid @RequestBody SendCodeRequest request) {
        return signupService.sendCode(request);
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify the code and claim an existing student account")
    public LoginResponse verify(@Valid @RequestBody ClaimExistingRequest request) {
        return signupService.verifyExisting(request);
    }

    @GetMapping("/username-available")
    @Operation(summary = "Is this student login name free? Returns alternatives when taken")
    public EmailAvailabilityResponse usernameAvailable(@RequestParam("username") String username) {
        return accountEmailService.check(username, Role.STUDENT);
    }

    @PostMapping("/new")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a brand-new student and create their account")
    public LoginResponse registerNew(@Valid @RequestBody StudentRegistrationRequest request) {
        return signupService.registerNew(request);
    }

    @PostMapping("/forgot/send-code")
    @Operation(summary = "Send a password-reset code to an existing student's WhatsApp")
    public SendCodeResponse sendResetCode(@Valid @RequestBody SendCodeRequest request) {
        return signupService.sendResetCode(request);
    }

    @PostMapping("/forgot/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Check a password-reset code before showing the reset step")
    public void verifyResetCode(@Valid @RequestBody ForgotVerifyRequest request) {
        signupService.verifyResetCode(request);
    }

    @PostMapping("/forgot/reset")
    @Operation(summary = "Set a new password after the reset code is verified. Auto-logs in")
    public LoginResponse resetPassword(@Valid @RequestBody VerifyExistingRequest request) {
        return signupService.resetPassword(request);
    }

    // --- Parent self-registration ------------------------------------------

    @GetMapping("/parent/check")
    @Operation(summary = "Confirm a Student Code exists (and has room) before parent signup")
    public ParentCheckResponse checkStudent(@RequestParam("serial") int serial) {
        return parentSignupService.checkStudent(serial);
    }

    @GetMapping("/parent/username-available")
    @Operation(summary = "Is this parent login name free? Returns alternatives when taken")
    public EmailAvailabilityResponse parentUsernameAvailable(@RequestParam("username") String username) {
        return accountEmailService.check(username, Role.PARENT);
    }

    @PostMapping("/parent")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a parent against a student; awaits the student's approval")
    public ParentPendingResponse registerParent(@Valid @RequestBody ParentRegistrationRequest request) {
        return parentSignupService.registerNew(request);
    }

    @PostMapping("/parent/forgot/send-code")
    @Operation(summary = "Send a password-reset code to a parent's WhatsApp")
    public SendCodeResponse sendParentResetCode(@Valid @RequestBody ParentForgotSendRequest request) {
        return parentSignupService.sendResetCode(request);
    }

    @PostMapping("/parent/forgot/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Check a parent's password-reset code before the reset step")
    public void verifyParentResetCode(@Valid @RequestBody ParentForgotVerifyRequest request) {
        parentSignupService.verifyResetCode(request);
    }

    @PostMapping("/parent/forgot/reset")
    @Operation(summary = "Set a parent's new password after the reset code is verified. Auto-logs in")
    public LoginResponse resetParentPassword(@Valid @RequestBody ParentResetRequest request) {
        return parentSignupService.resetPassword(request);
    }
}
