package com.center.group.service;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.group.dto.GroupRequest;
import com.center.group.dto.GroupResponse;
import com.center.group.entity.Group;
import com.center.common.exception.BusinessRuleException;
import com.center.common.exception.DuplicateResourceException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.group.mapper.GroupMapper;
import com.center.group.repository.GroupRepository;
import com.center.group.service.GroupService;
import com.center.student.repository.StudentRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private static final String NOT_FOUND = "المجموعة غير موجودة";
    private static final String DUPLICATE_SLOT = "يوجد مجموعة أخرى في نفس اليوم والوقت";

    private final GroupRepository groupRepository;
    private final StudentRepository studentRepository;
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

    /**
     * The offline replay path. The slot check still runs and can still refuse:
     * a device with no line cannot know another already took Saturday 16:00, so
     * this is the only place that clash can surface, and it has to surface
     * rather than one group silently landing on top of the other.
     */
    @Override
    @Transactional
    public GroupResponse upsert(UUID groupId, GroupRequest request) {
        short day = request.dayOfWeek().shortValue();
        LocalTime start = LocalTime.parse(request.startTime());
        if (groupRepository.existsByDayOfWeekAndStartTimeAndIdNot(day, start, groupId)) {
            throw new DuplicateResourceException(DUPLICATE_SLOT);
        }
        Group group = groupRepository.findById(groupId).orElse(null);
        if (group == null) {
            group = new Group();
            group.setId(groupId);
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
    // noRollbackFor: idempotent sync delete catches "already gone"; the RNF is
    // raised before any write (see RegistrationServiceImpl.unregister).
    @Transactional(noRollbackFor = ResourceNotFoundException.class)
    public void delete(UUID groupId, UUID transferToGroupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));

        // No student may be left without a group: transfer them to the chosen
        // target first. A group with no students needs no target.
        long students = studentRepository.countByGroup_Id(groupId);
        if (students > 0) {
            if (transferToGroupId == null) {
                throw new BusinessRuleException("اختر مجموعة لنقل طلاب هذه المجموعة إليها قبل حذفها");
            }
            if (transferToGroupId.equals(groupId)) {
                throw new BusinessRuleException("لا يمكن نقل الطلاب إلى نفس المجموعة");
            }
            Group target = groupRepository.findById(transferToGroupId)
                    .orElseThrow(() -> new ResourceNotFoundException("مجموعة النقل غير موجودة"));
            if (target.isDeleted()) {
                throw new BusinessRuleException("لا يمكن النقل إلى مجموعة محذوفة");
            }
            if (!target.getGrade().equals(group.getGrade())) {
                throw new BusinessRuleException("مجموعة النقل يجب أن تكون في نفس الصف");
            }
            studentRepository.reassignGroup(groupId, target);
        }

        // Soft delete: keep the row (past registrations/attendance still resolve its
        // label), but hide it from every forward-looking picker.
        group.setActive(false);
        group.setDeleted(true);
        groupRepository.save(group);
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
        // Null means "not stated": the online form has a separate PATCH for the
        // flag and must not switch a group off just by leaving the field out.
        if (request.isActive() != null) {
            group.setActive(request.isActive());
        }
    }
}
