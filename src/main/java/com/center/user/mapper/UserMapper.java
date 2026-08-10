package com.center.user.mapper;
import com.center.auth.service.AuthServiceImpl;
import com.center.auth.dto.AuthenticatedUserResponse;

import java.util.List;

import org.mapstruct.Mapper;

import com.center.user.dto.AssistantResponse;
import com.center.user.entity.User;

@Mapper
public interface UserMapper {

    // UserResponse is assembled in UserServiceImpl: its permissions come from the
    // RBAC grants, not from the User entity.

    AssistantResponse toAssistantResponse(User user);

    List<AssistantResponse> toAssistantResponses(List<User> users);

    // AuthenticatedUserResponse is built in AuthServiceImpl: its permissions/modules
    // are resolved from the RBAC model, not mapped from the User entity.
}
