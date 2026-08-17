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

import org.springframework.context.ApplicationEventPublisher;
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
import com.center.messaging.event.AttendanceRecordedEvent;
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
    private final ApplicationEventPublisher events;

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
                        row.getHasExam(),
                        row.getHomeworkFlag()))
                .toList();
    }

    @Override
    @Transactional
    public RegistrationResponse register(CreateRegistrationRequest request) {
        // A student may attend the same lesson again under a DIFFERENT group (a
        // confirmed repeat, gated in the UI), so a duplicate is one that repeats
        // the SAME group. A group-less registration keeps the old lesson-wide
        // guard, since there is no group to distinguish repeats.
        boolean duplicate = request.groupId() == null
                ? registrationRepository.existsByLectureIdAndStudentId(request.lectureId(), request.studentId())
                : registrationRepository.existsByLectureIdAndStudentIdAndGroupId(
                        request.lectureId(), request.studentId(), request.groupId());
        if (duplicate) {
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
        registration.setAttendedAt(request.attendedAtOrNow());
        registrationRepository.saveAndFlush(registration);

        // Keeps the group attendance log live - it feeds the Groups cards'
        // آخر حضور and counts. One row per (group, student, day).
        if (group != null) {
            attendanceRepository.logToday(group.getId(), student.getId(), TenantContext.get());
            // Fires the automated attendance message AFTER this commits.
            events.publishEvent(new AttendanceRecordedEvent(
                    TenantContext.get(), student.getId(), group.getId(), lecture.getId()));
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
        // Saving a mark sends nothing. Grades leave the building only when the
        // teacher presses send on the lesson's roster, once the column is done -
        // a mark typed here is as likely to be corrected in the next minute as
        // it is to be final, and a message already read cannot be corrected.
        return reload(registrationRepository.saveAndFlush(registration));
    }

    /**
     * The offline replay path.
     *
     * <p>The row is resolved by its NATURAL key first, not by the client's id:
     * two devices registering the same student for the same lesson under the same
     * group mint two different row ids for what the database treats as one row,
     * and inserting the second would break {@code unique (lecture_id, student_id,
     * group_id)}. Finding that row and updating it converges instead - which is
     * why registrations, alone among these entities, cannot produce a conflict.
     * A different group is a distinct row (a repeat attendance), by design.
     *
     * <p>The blocked-student rule still runs: a student blocked while the device
     * was offline must not slip in through a queued registration.
     */
    @Override
    @Transactional
    public RegistrationResponse upsert(UUID registrationId, CreateRegistrationRequest request,
            BigDecimal examScore) {
        // Resolve on the full natural key (lecture, student, group) so a repeat in
        // another group is its own row rather than colliding with the first.
        Registration registration = (request.groupId() == null
                ? registrationRepository.findByLectureIdAndStudentId(request.lectureId(), request.studentId())
                : registrationRepository.findByLectureIdAndStudentIdAndGroupId(
                        request.lectureId(), request.studentId(), request.groupId()))
                .orElse(null);
        // Only a brand-new attendance fires the message; a replayed edit does not.
        boolean created = registration == null;

        if (registration == null) {
            Lecture lecture = lectureRepository.findById(request.lectureId())
                    .orElseThrow(() -> new ResourceNotFoundException("الحصة غير موجودة"));
            Student student = studentRepository.findById(request.studentId())
                    .orElseThrow(() -> new ResourceNotFoundException("الطالب غير موجود"));
            if (!student.isActive()) {
                String reason = student.getBlockReason();
                throw new BusinessRuleException(reason == null || reason.isBlank()
                        ? "الطالب محظور ولا يمكن تسجيله في الحصص"
                        : "الطالب محظور: " + reason);
            }
            registration = new Registration();
            registration.setId(registrationId);
            registration.setLecture(lecture);
            registration.setStudent(student);
            // The device's own reading of when the student walked in. Set only on
            // creation: a replayed edit must never move the attendance time.
            registration.setAttendedAt(request.attendedAtOrNow());
        }

        Group group = request.groupId() == null ? null : groupRepository.findById(request.groupId())
                .orElseThrow(() -> new ResourceNotFoundException("المجموعة غير موجودة"));
        registration.setGroup(group);
        registration.setStatus(request.statusOrDefault());
        registration.setHomeworkFlag(request.homeworkFlag());

        BigDecimal maximum = maximumGrade(registration.getLecture().getExamGrade());
        if (examScore != null && maximum != null && examScore.compareTo(maximum) > 0) {
            throw new BusinessRuleException(
                    "الدرجة لا يمكن أن تتجاوز " + maximum.stripTrailingZeros().toPlainString());
        }
        registration.setExamScore(examScore);
        registrationRepository.saveAndFlush(registration);

        if (group != null) {
            attendanceRepository.logToday(group.getId(), registration.getStudent().getId(),
                    TenantContext.get());
            if (created) {
                events.publishEvent(new AttendanceRecordedEvent(TenantContext.get(),
                        registration.getStudent().getId(), group.getId(),
                        registration.getLecture().getId()));
            }
        }
        return reload(registration);
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
