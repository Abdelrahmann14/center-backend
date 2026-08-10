package com.center.registration.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.registration.dto.CreateRegistrationRequest;
import com.center.registration.dto.RegistrationFilter;
import com.center.registration.dto.UpdateHomeworkRequest;
import com.center.lecture.dto.LessonGroupResponse;
import com.center.lecture.dto.LessonHistoryResponse;
import com.center.analytics.dto.PriceBucketResponse;
import com.center.registration.dto.RegistrationResponse;
import com.center.group.entity.Group;
import com.center.lecture.entity.Lecture;
import com.center.registration.entity.Registration;
import com.center.student.entity.Student;
import com.center.common.enums.Gender;
import com.center.common.enums.RegistrationStatus;
import com.center.common.exception.BusinessRuleException;
import com.center.common.exception.DuplicateResourceException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.registration.mapper.RegistrationMapper;
import com.center.lecture.repository.AttendanceRepository;
import com.center.group.repository.GroupRepository;
import com.center.lecture.repository.LectureRepository;
import com.center.registration.repository.RegistrationRepository;
import com.center.student.repository.StudentRepository;
import com.center.registration.service.RegistrationService;
import com.center.common.tenant.TenantContext;
import com.center.registration.specification.RegistrationSpecifications;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    private static final String NOT_FOUND = "التسجيل غير موجود";
    private static final String ALREADY_REGISTERED = "الطالب مسجّل بالفعل في هذه الحصة";

    /** exam_grade is free text ("50", "50 درجة"); the cap is its first number. */
    private static final Pattern FIRST_NUMBER = Pattern.compile("\\d+(\\.\\d+)?");

    private final RegistrationRepository registrationRepository;
    private final StudentRepository studentRepository;
    private final LectureRepository lectureRepository;
    private final GroupRepository groupRepository;
    private final AttendanceRepository attendanceRepository;
    private final RegistrationMapper registrationMapper;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public Page<RegistrationResponse> search(RegistrationFilter filter, Pageable pageable) {
        return registrationRepository.findAll(RegistrationSpecifications.matching(filter), pageable)
                .map(registrationMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LessonGroupResponse> lessonGroups(UUID lectureId) {
        return registrationRepository.countByGroup(lectureId, RegistrationStatus.PRESENT).stream()
                .map(row -> new LessonGroupResponse(row.getGroupId(), row.getCount(), row.getAttendedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceBucketResponse> statsByPrice(UUID lectureId) {
        Map<BigDecimal, Bucket> buckets = new LinkedHashMap<>();

        for (var row : registrationRepository.findPriceStats(lectureId, RegistrationStatus.PRESENT)) {
            // A null price is a legitimate bucket: the student has none set.
            buckets.computeIfAbsent(row.getPrice(), key -> new Bucket()).add(row);
        }

        List<PriceBucketResponse> result = new ArrayList<>();
        buckets.entrySet().stream()
                // No-price bucket last, the rest ascending.
                .sorted(Comparator
                        .comparing((Map.Entry<BigDecimal, Bucket> e) -> e.getKey() == null)
                        .thenComparing(e -> e.getKey() == null ? BigDecimal.ZERO : e.getKey()))
                .forEach(entry -> result.add(entry.getValue().toResponse(entry.getKey())));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LessonHistoryResponse> historyForStudent(UUID studentId) {
        return registrationRepository.findHistoryForStudent(studentId).stream()
                .map(row -> new LessonHistoryResponse(
                        // The LECTURE id - unique per card, attended or not.
                        row.getId(),
                        row.getLectureName(),
                        // No registration row for a grade lesson means absent.
                        row.getStatus() == null ? RegistrationStatus.ABSENT : row.getStatus(),
                        row.getExamScore(),
                        row.getExamGrade(),
                        row.getHomeworkFlag()))
                .toList();
    }

    @Override
    @Transactional
    public RegistrationResponse register(CreateRegistrationRequest request) {
        if (registrationRepository.existsByLectureIdAndStudentId(request.lectureId(), request.studentId())) {
            throw new DuplicateResourceException(ALREADY_REGISTERED);
        }

        Lecture lecture = lectureRepository.findById(request.lectureId())
                .orElseThrow(() -> new ResourceNotFoundException("الحصة غير موجودة"));
        Student student = studentRepository.findById(request.studentId())
                .orElseThrow(() -> new ResourceNotFoundException("الطالب غير موجود"));
        // A blocked student keeps their record and history but cannot attend, so
        // the block is enforced here rather than only hidden in the UI.
        if (!student.isActive()) {
            String reason = student.getBlockReason();
            throw new BusinessRuleException(reason == null || reason.isBlank()
                    ? "الطالب محظور ولا يمكن تسجيله في الحصص"
                    : "الطالب محظور: " + reason);
        }
        Group group = request.groupId() == null ? null : groupRepository.findById(request.groupId())
                .orElseThrow(() -> new ResourceNotFoundException("المجموعة غير موجودة"));

        Registration registration = new Registration();
        registration.setLecture(lecture);
        registration.setStudent(student);
        registration.setGroup(group);
        registration.setStatus(request.statusOrDefault());
        registration.setHomeworkFlag(request.homeworkFlag());
        registrationRepository.saveAndFlush(registration);

        // Keeps the group attendance log live - it feeds the Groups cards'
        // آخر حضور and counts. One row per (group, student, day).
        if (group != null) {
            attendanceRepository.logToday(group.getId(), student.getId(), TenantContext.get());
        }

        return reload(registration);
    }

    @Override
    @Transactional
    public RegistrationResponse updateHomework(UUID registrationId, UpdateHomeworkRequest request) {
        Registration registration = findEntity(registrationId);
        registration.setHomeworkFlag(request.homeworkFlag());
        return reload(registrationRepository.saveAndFlush(registration));
    }

    @Override
    @Transactional
    public RegistrationResponse updateExamScore(UUID registrationId, BigDecimal examScore) {
        Registration registration = findEntity(registrationId);

        BigDecimal maximum = maximumGrade(registration.getLecture().getExamGrade());
        if (examScore != null && maximum != null && examScore.compareTo(maximum) > 0) {
            throw new BusinessRuleException(
                    "الدرجة لا يمكن أن تتجاوز " + maximum.stripTrailingZeros().toPlainString());
        }

        registration.setExamScore(examScore);
        return reload(registrationRepository.saveAndFlush(registration));
    }

    @Override
    @Transactional
    public void unregister(UUID registrationId) {
        if (!registrationRepository.existsById(registrationId)) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        registrationRepository.deleteById(registrationId);
    }

    private Registration findEntity(UUID registrationId) {
        return registrationRepository.findWithStudentById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    /**
     * totalLessons is a @Formula, so it is only populated by a select - refresh
     * after writing or the response would carry a stale count.
     */
    private RegistrationResponse reload(Registration registration) {
        entityManager.refresh(registration);
        return registrationMapper.toResponse(registration);
    }

    private static BigDecimal maximumGrade(String examGrade) {
        if (examGrade == null) {
            return null;
        }
        Matcher matcher = FIRST_NUMBER.matcher(examGrade);
        return matcher.find() ? new BigDecimal(matcher.group()) : null;
    }

    /** Mutable tally for one price bucket. */
    private static final class Bucket {

        private long count;
        private long male;
        private long female;
        private long otherGroup;
        private long newStudents;

        void add(RegistrationRepository.PriceStatRow row) {
            count++;
            if (row.getGender() == Gender.MALE) {
                male++;
            } else if (row.getGender() == Gender.FEMALE) {
                female++;
            }
            UUID registered = row.getRegisteredGroupId();
            UUID assigned = row.getAssignedGroupId();
            if (registered != null && assigned != null && !registered.equals(assigned)) {
                otherGroup++;
            }
            // Exactly one lesson overall means this one is their first.
            if (row.getTotalLessons() == 1L) {
                newStudents++;
            }
        }

        PriceBucketResponse toResponse(BigDecimal price) {
            return new PriceBucketResponse(price, count, male, female, otherGroup, newStudents);
        }
    }
}
