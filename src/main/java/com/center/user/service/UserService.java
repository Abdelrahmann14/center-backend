package com.center.user.service;

import java.util.List;
import java.util.UUID;

import com.center.user.dto.CreateUserRequest;
import com.center.user.dto.UpdateUserRequest;
import com.center.user.dto.AssistantResponse;
import com.center.user.dto.UserResponse;

public interface UserService {

    List<UserResponse> findAll();

    /** Assistants only - readable by any authenticated user. */
    List<AssistantResponse> findAssistants();

    /** True when the (globally-unique) username is free to claim. */
    boolean isUsernameAvailable(String username);

    UserResponse create(CreateUserRequest request);

    UserResponse update(UUID userId, UpdateUserRequest request);

    /** @throws com.center.common.exception.ApplicationException if the target is the admin */
    void delete(UUID userId);
}
