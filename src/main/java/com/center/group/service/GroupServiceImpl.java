package com.center.group.service;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.group.dto.GroupRequest;
import com.center.group.dto.GroupResponse;
import com.center.group.entity.Group;
import com.center.common.exception.DuplicateResourceException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.group.mapper.GroupMapper;
import com.center.group.repository.GroupRepository;
import com.center.group.service.GroupService;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private static final String NOT_FOUND = "المجموعة غير موجودة";
    private static final String DUPLICATE_SLOT = "يوجد مجموعة أخرى في نفس اليوم والوقت";

    private final GroupRepository groupRepository;
    private final GroupMapper groupMapper;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public List<GroupResponse> findAll() {
        return groupMapper.toResponses(groupRepository.findAllByOrderByDayOfWeekAscStartTimeAsc());
    }

    @Override
    @Transactional
    public GroupResponse create(GroupRequest request) {
        short day = request.dayOfWeek().shortValue();
        LocalTime start = LocalTime.parse(request.startTime());
        if (groupRepository.existsByDayOfWeekAndStartTime(day, start)) {
            throw new DuplicateResourceException(DUPLICATE_SLOT);
        }
        Group group = new Group();
        apply(group, request, day, start);
        return refresh(groupRepository.save(group));
    }

    @Override
    @Transactional
    public GroupResponse update(UUID groupId, GroupRequest request) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        short day = request.dayOfWeek().shortValue();
        LocalTime start = LocalTime.parse(request.startTime());
        if (groupRepository.existsByDayOfWeekAndStartTimeAndIdNot(day, start, groupId)) {
            throw new DuplicateResourceException(DUPLICATE_SLOT);
        }
        apply(group, request, day, start);
        return refresh(groupRepository.save(group));
    }

    @Override
    @Transactional
    public GroupResponse setActive(UUID groupId, boolean active) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        group.setActive(active);
        return refresh(groupRepository.save(group));
    }

    @Override
    @Transactional
    public void delete(UUID groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        groupRepository.deleteById(groupId);
    }

    /**
     * The card's counts and price are @Formula columns: they are computed by a
     * select, never by the insert/update. A findById here would just hand back
     * the entity already in the persistence context with those fields stale, so
     * force a re-read from the database.
     */
    private GroupResponse refresh(Group group) {
        groupRepository.flush();
        entityManager.refresh(group);
        return groupMapper.toResponse(group);
    }

    private static void apply(Group group, GroupRequest request, short day, LocalTime start) {
        group.setDayOfWeek(day);
        group.setStartTime(start);
        group.setCenterName(request.centerName().strip());
        group.setGrade(request.grade().strip());
    }
}
