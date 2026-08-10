package com.center.account.service;

import com.center.account.dto.ChangePasswordRequest;
import com.center.account.dto.ChangePhoneRequest;
import com.center.account.dto.AccountResponse;
import com.center.auth.security.AuthenticatedUser;

/** The unified account page, shared by every role. */
public interface AccountService {

    /** The signed-in account's own details (code and phone vary by role). */
    AccountResponse get(AuthenticatedUser principal);

    /** Changes the account's own password after confirming the current one. */
    void changePassword(ChangePasswordRequest request, AuthenticatedUser principal);

    /**
     * Changes the account's own phone. For a parent this re-syncs the number onto
     * every linked student; for a student it updates their own (globally-unique)
     * number. Other roles have no editable phone.
     */
    void changePhone(ChangePhoneRequest request, AuthenticatedUser principal);
}
