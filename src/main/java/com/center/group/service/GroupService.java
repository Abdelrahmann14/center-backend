package com.center.group.service;

import java.util.List;
import java.util.UUID;

import com.center.group.dto.GroupRequest;
import com.center.group.dto.GroupResponse;

public interface GroupService {

    List<GroupResponse> findAll();

    GroupResponse create(GroupRequest request);

    GroupResponse update(UUID groupId, GroupRequest request);

    GroupResponse setActive(UUID groupId, boolean active);

    void delete(UUID groupId);
}
