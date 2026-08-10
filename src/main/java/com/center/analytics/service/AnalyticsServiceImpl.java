package com.center.analytics.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.analytics.dto.AnalyticsResponse;
import com.center.analytics.dto.DayCountResponse;
import com.center.common.enums.Role;
import com.center.center.repository.CenterRepository;
import com.center.group.repository.GroupRepository;
import com.center.student.repository.StudentRepository;
import com.center.user.repository.UserRepository;
import com.center.analytics.service.AnalyticsService;
import com.center.common.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final GroupRepository groupRepository;
    private final CenterRepository centerRepository;

    @Override
    @Transactional(readOnly = true)
    public AnalyticsResponse summary() {
        List<DayCountResponse> groupsByDay = groupRepository.countByDayOfWeek().stream()
                .map(row -> new DayCountResponse(row.getDayOfWeek(), row.getCount()))
                .toList();

        return new AnalyticsResponse(
                // Users are not @TenantId, so the assistant count is scoped by hand;
                // the other counts run through @TenantId repositories and self-scope.
                userRepository.countByRoleAndAdminId(Role.USER, TenantContext.get()),
                studentRepository.countByActiveTrue(),
                groupRepository.count(),
                centerRepository.count(),
                groupsByDay);
    }
}
