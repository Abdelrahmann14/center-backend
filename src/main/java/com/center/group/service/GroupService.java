package com.center.group.service;

import java.util.List;
import java.util.UUID;

import com.center.group.dto.GroupRequest;
import com.center.group.dto.GroupResponse;

public interface GroupService {

    List<GroupResponse> findAll();

    GroupResponse create(GroupRequest request);

    GroupResponse update(UUID groupId, GroupRequest request);

    /**
     * Create or update under an id the CLIENT chose, for replaying a write made
     * offline. Runs the same slot-clash check as the online paths - two devices
     * can both claim one day and time, and only one of them can have it.
     */
    GroupResponse upsert(UUID groupId, GroupRequest request);

    GroupResponse setActive(UUID groupId, boolean active);

    /**
     * Soft-delete a group. Its past registrations/attendance stay (history keeps its
     * label), but it is hidden from every forward-looking picker. Any students still
     * assigned to it are first transferred to {@code transferToGroupId} (required
     * only when the group has students) so no student is left without a group.
     */
    void delete(UUID groupId, UUID transferToGroupId);
}
