package com.center.student.service;
import com.center.google.event.GoogleContactEvents;
import com.center.google.repository.GoogleContactLinkRepository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.common.constants.ValidationRules;
import com.center.student.dto.StudentFilter;
import com.center.student.dto.StudentRequest;
import com.center.student.dto.StudentDuplicateResponse;
import com.center.student.dto.StudentOptionsResponse;
import com.center.student.dto.StudentResponse;
import com.center.group.entity.Group;
import com.center.student.entity.Student;
import com.center.common.exception.BusinessRuleException;
import com.center.common.exception.DuplicateResourceException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.student.mapper.StudentMapper;
import com.center.center.repository.CenterGradeRepository;
import com.center.group.repository.GroupRepository;
import com.center.student.repository.StudentRepository;
import com.center.student.service.StudentService;
import com.center.student.specification.StudentSpecifications;
import com.center.common.tenant.TenantContext;
import com.center.common.util.TextUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private static final String NOT_FOUND = "الطالب غير موجود";
    private static final String GROUP_NOT_FOUND = "المجموعة غير موجودة";
    private static final String DUPLICATE_NAME = "يوجد طالب آخر بنفس الاسم";
    private static final String DUPLICATE_PHONE = "رقم هاتف الطالب مستخدم لطالب آخر";

    /** Prices are money - anything under a tenth of a piastre is the same price. */
    private static final BigDecimal PRICE_EPSILON = new BigDecimal("0.001");

    private final StudentRepository studentRepository;
    private final GroupRepository groupRepository;
    private final CenterGradeRepository centerGradeRepository;
    private final StudentMapper studentMapper;
    private final com.center.google.repository.GoogleContactLinkRepository googleContactLinkRepository;
    private final org.springframework.context.ApplicationEventPublisher events;

    @Override
    @Transactional(readOnly = true)
    public Page<StudentResponse> search(StudentFilter filter, Pageable pageable) {
        UUID adminId = TenantContext.get();
        Set<UUID> synced = adminId == null
                ? Set.of()
                : new java.util.HashSet<>(googleContactLinkRepository.findSyncedSubjectIds(adminId));
        return studentRepository.findAll(StudentSpecifications.matching(filter), pageable)
                .map(studentMapper::toResponse)
                .map(r -> withSynced(r, synced.contains(r.id())));
    }

    /** Copy of a mapped response with the Google-sync flag filled in. */
    private static StudentResponse withSynced(StudentResponse r, boolean synced) {
        return new StudentResponse(r.id(), r.serial(), r.name(), r.grade(), r.school(), r.city(),
                r.gender(), r.groupId(), r.studentPhones(), r.parentPhones(), r.religion(),
                r.academicTrack(), r.lessonPrice(), r.isDiscounted(), r.notes(), r.isActive(),
                r.blockReason(), r.registered(), synced, r.createdAt(), r.createdBy(),
                r.updatedAt(), r.updatedBy());
    }

    @Override
    @Transactional(readOnly = true)
    public StudentOptionsResponse options() {
        return new StudentOptionsResponse(
                studentRepository.findDistinctSchools(),
                studentRepository.findDistinctCities(),
                studentRepository.findMaxSerial() + 1);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentDuplicateResponse checkDuplicates(String name, List<String> phones, UUID excludeId) {
        UUID exclude = excludeId == null ? StudentRepository.NO_EXCLUSION : excludeId;

        boolean nameTaken = name != null && !name.isBlank()
                && studentRepository.existsByNameAndIdNot(name.strip(), exclude);

        List<String> digits = (phones == null ? List.<String>of() : phones).stream()
                .map(TextUtils::digitsOnly)
                .filter(phone -> !phone.isEmpty())
                .toList();

        Map<String, String> owners = new LinkedHashMap<>();
        if (!digits.isEmpty()) {
            Set<String> wanted = new LinkedHashSet<>(digits);
            for (var owner : studentRepository.findPhoneOwners(String.join(",", digits), exclude, currentAdmin())) {
                // A row matched because it shares at least one number; report
                // only the numbers actually asked about.
                for (String phone : owner.getPhones().split(",")) {
                    if (wanted.contains(phone)) {
                        owners.putIfAbsent(phone, owner.getName());
                    }
                }
            }
        }
        return new StudentDuplicateResponse(nameTaken, owners);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentResponse findById(UUID studentId) {
        return studentMapper.toResponse(studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND)));
    }

    @Override
    @Transactional
    public StudentResponse create(StudentRequest request) {
        Student student = new Student();
        apply(student, request, null);
        Student saved = studentRepository.save(student);
        events.publishEvent(new com.center.google.event.GoogleContactEvents.StudentChanged(
                TenantContext.get(), saved.getId()));
        return studentMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public StudentResponse update(UUID studentId, StudentRequest request) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        apply(student, request, studentId);
        Student saved = studentRepository.save(student);
        events.publishEvent(new com.center.google.event.GoogleContactEvents.StudentChanged(
                TenantContext.get(), saved.getId()));
        return studentMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        studentRepository.deleteById(studentId);
    }

    private void apply(Student student, StudentRequest request, UUID excludeId) {
        String[] studentPhones = normalisePhones(request.studentPhones(), "الطالب");
        String[] parentPhones = normalisePhones(request.parentPhones(), "ولي الأمر");
        String name = request.name().strip();

        assertUnique(name, studentPhones, excludeId, request.allowsDuplicatePhone());

        Group group = resolveGroup(request.groupId());
        ResolvedPrice price = resolvePrice(group, request.lessonPrice());

        student.setName(name);
        student.setGrade(request.grade().strip());
        student.setSchool(TextUtils.blankToNull(request.school()));
        student.setCity(TextUtils.blankToNull(request.city()));
        student.setGender(request.gender());
        student.setGroup(group);
        student.setStudentPhones(studentPhones);
        student.setParentPhones(parentPhones);
        student.setReligion(request.religion());
        student.setAcademicTrack(request.academicTrack());
        student.setLessonPrice(price.value());
        student.setDiscounted(price.discounted());
        student.setNotes(TextUtils.blankToNull(request.notes()));

        // Blocking keeps the record and the history; only registration is barred.
        // The reason is dropped on unblock so a stale explanation cannot linger.
        boolean active = request.activeOrDefault();
        student.setActive(active);
        student.setBlockReason(active ? null : TextUtils.blankToNull(request.blockReason()));
    }

    /** Strips formatting, then enforces count, length and uniqueness. */
    private static String[] normalisePhones(List<String> phones, String owner) {
        List<String> digits = (phones == null ? List.<String>of() : phones).stream()
                .map(TextUtils::digitsOnly)
                .filter(phone -> !phone.isEmpty())
                .toList();

        if (digits.isEmpty()) {
            throw new BusinessRuleException("أدخل رقم هاتف واحد " + owner + " على الأقل");
        }
        if (digits.size() > ValidationRules.MAX_PHONES) {
            throw new BusinessRuleException("بحد أقصى " + ValidationRules.MAX_PHONES + " أرقام " + owner);
        }
        boolean wrongLength = digits.stream().anyMatch(p -> p.length() != ValidationRules.PHONE_DIGITS);
        if (wrongLength) {
            throw new BusinessRuleException(
                    "رقم الهاتف يجب أن يكون " + ValidationRules.PHONE_DIGITS + " رقماً");
        }
        if (new LinkedHashSet<>(digits).size() != digits.size()) {
            throw new BusinessRuleException("أرقام هاتف " + owner + " مكررة");
        }
        return digits.toArray(String[]::new);
    }

    private void assertUnique(String name, String[] phones, UUID excludeId, boolean allowDuplicatePhone) {
        UUID exclude = excludeId == null ? StudentRepository.NO_EXCLUSION : excludeId;
        if (studentRepository.existsByNameAndIdNot(name, exclude)) {
            throw new DuplicateResourceException(DUPLICATE_NAME);
        }
        // Siblings legitimately share a parent's phone, so the UI can override.
        if (!allowDuplicatePhone
                && studentRepository.phoneTaken(String.join(",", phones), exclude, currentAdmin())) {
            throw new DuplicateResourceException(DUPLICATE_PHONE);
        }
    }

    /** The workspace acting on this request; bound by the JWT filter. */
    private static UUID currentAdmin() {
        return TenantContext.get();
    }

    private Group resolveGroup(UUID groupId) {
        if (groupId == null) {
            return null;
        }
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException(GROUP_NOT_FOUND));
    }

    /** A price and whether it counts as a discount off the center's rate. */
    private record ResolvedPrice(BigDecimal value, boolean discounted) {
    }

    /**
     * Defaults to the center's price for the group's grade. A student may pay
     * less (a discount) but never more.
     */
    private ResolvedPrice resolvePrice(Group group, BigDecimal requested) {
        Optional<BigDecimal> centerPrice = group == null
                ? Optional.empty()
                : centerGradeRepository.findPriceForGroup(group.getId());

        if (centerPrice.isEmpty()) {
            return new ResolvedPrice(requested, false);
        }
        BigDecimal center = centerPrice.get();
        BigDecimal price = requested == null ? center : requested;

        if (price.compareTo(center.add(PRICE_EPSILON)) > 0) {
            throw new BusinessRuleException("لا يمكن أن يكون السعر أعلى من سعر السنتر");
        }
        return new ResolvedPrice(price, price.compareTo(center.subtract(PRICE_EPSILON)) < 0);
    }
}
