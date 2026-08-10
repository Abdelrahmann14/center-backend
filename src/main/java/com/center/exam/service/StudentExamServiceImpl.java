package com.center.exam.service;
import com.center.grade.entity.Grade;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.center.exam.dto.StudentAnswerInput;
import com.center.exam.dto.StudentExamSubmitRequest;
import com.center.exam.dto.StudentExamChoiceView;
import com.center.exam.dto.StudentExamDetail;
import com.center.exam.dto.StudentExamQuestionResult;
import com.center.exam.dto.StudentExamQuestionView;
import com.center.exam.dto.StudentExamResult;
import com.center.exam.dto.StudentExamSummary;
import com.center.exam.entity.Exam;
import com.center.exam.entity.ExamAnswer;
import com.center.exam.entity.ExamAttempt;
import com.center.exam.entity.ExamChoice;
import com.center.exam.entity.ExamQuestion;
import com.center.group.entity.Group;
import com.center.lecture.entity.Lecture;
import com.center.parent.entity.Parent;
import com.center.parent.entity.ParentStudentLink;
import com.center.registration.entity.Registration;
import com.center.student.entity.Student;
import com.center.common.enums.LinkStatus;
import com.center.common.enums.NotificationType;
import com.center.common.enums.RegistrationStatus;
import com.center.common.exception.BusinessRuleException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.lecture.repository.AttendanceRepository;
import com.center.exam.repository.ExamAnswerRepository;
import com.center.exam.repository.ExamAttemptRepository;
import com.center.exam.repository.ExamChoiceRepository;
import com.center.exam.repository.ExamGroupPasswordRepository;
import com.center.exam.repository.ExamQuestionRepository;
import com.center.exam.repository.ExamRepository;
import com.center.lecture.repository.LectureRepository;
import com.center.parent.repository.ParentRepository;
import com.center.parent.repository.ParentStudentLinkRepository;
import com.center.registration.repository.RegistrationRepository;
import com.center.student.repository.StudentRepository;
import com.center.user.repository.UserRepository;
import com.center.whatsapp.service.GreenApiClient;
import com.center.notification.service.MessageTemplateService;
import com.center.notification.service.NotificationService;
import com.center.exam.service.StudentExamService;
import com.center.common.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentExamServiceImpl implements StudentExamService {

    private static final String STUDENT_NOT_FOUND = "الطالب غير موجود";
    private static final String EXAM_NOT_AVAILABLE = "الاختبار غير متاح";
    private static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");

    private final StudentRepository studentRepository;
    private final ExamRepository examRepository;
    private final ExamQuestionRepository questionRepository;
    private final ExamChoiceRepository choiceRepository;
    private final ExamGroupPasswordRepository groupPasswordRepository;
    private final ExamAttemptRepository attemptRepository;
    private final ExamAnswerRepository answerRepository;
    private final LectureRepository lectureRepository;
    private final RegistrationRepository registrationRepository;
    private final AttendanceRepository attendanceRepository;
    private final ParentStudentLinkRepository parentLinkRepository;
    private final ParentRepository parentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final MessageTemplateService templateService;
    private final GreenApiClient greenApiClient;

    @Override
    @Transactional(readOnly = true)
    public List<StudentExamSummary> available(UUID studentUserId) {
        Student student = requireStudent(studentUserId);
        Group group = student.getGroup();
        if (group == null) {
            return List.of();
        }
        OffsetDateTime now = OffsetDateTime.now();

        List<StudentExamSummary> result = new ArrayList<>();
        for (Exam exam : examRepository.findByDeletedAtIsNullOrderByGradeAscCreatedAtAsc()) {
            if (!isEligible(exam, group)) {
                continue;
            }
            ExamAttempt attempt = attemptRepository.findByExamIdAndStudentId(exam.getId(), student.getId()).orElse(null);
            OffsetDateTime until = availableUntil(exam, group);
            // Hide an exam only when it has expired AND was never started.
            if (attempt == null && until != null && now.isAfter(until)) {
                continue;
            }
            result.add(new StudentExamSummary(
                    exam.getId(), exam.getName(), exam.getGrade(), lectureName(exam.getLectureId()),
                    exam.getDurationMinutes(), exam.getMaxScore(), exam.getScheduledDate(), until,
                    statusOf(attempt), attempt == null ? null : attempt.getScore(),
                    attempt == null ? null : attempt.getBonusScore(), liveVersion(exam)));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public StudentExamDetail open(UUID examId, UUID studentUserId) {
        Student student = requireStudent(studentUserId);
        Group group = student.getGroup();
        Exam exam = requireEligibleExam(examId, group);

        ExamAttempt attempt = attemptRepository.findByExamIdAndStudentId(examId, student.getId()).orElse(null);
        OffsetDateTime until = availableUntil(exam, group);
        if (attempt == null && until != null && OffsetDateTime.now().isAfter(until)) {
            throw new BusinessRuleException("انتهت مدة إتاحة الاختبار");
        }

        List<StudentExamQuestionView> questions = new ArrayList<>();
        for (ExamQuestion q : questionRepository.findByExamIdOrderByPositionAsc(examId)) {
            questions.add(new StudentExamQuestionView(
                    q.getId(), q.getText(), q.getScore(), q.isAllowMultiple(), q.isBonus(),
                    q.getBonusScore(), q.getNote(), choiceViews(q.getId())));
        }

        return new StudentExamDetail(
                exam.getId(), exam.getName(), exam.getGrade(), lectureName(exam.getLectureId()),
                exam.getDurationMinutes(), exam.getMaxScore(), exam.getLabelStyle(),
                exam.isAllowMultipleCorrect(), exam.isBonusEnabled(), exam.isNotesEnabled(),
                exam.getScheduledDate(), until, groupPassword(exam, group), statusOf(attempt),
                attempt == null ? null : attempt.getScore(), attempt == null ? null : attempt.getBonusScore(),
                liveVersion(exam), questions);
    }

    @Override
    @Transactional
    public StudentExamResult submit(UUID examId, UUID studentUserId, StudentExamSubmitRequest request) {
        Student student = requireStudent(studentUserId);
        Exam exam = requireEligibleExam(examId, student.getGroup());
        ExamAttempt existing = attemptRepository.findByExamIdAndStudentId(examId, student.getId()).orElse(null);
        if (existing != null && "submitted".equals(existing.getStatus())) {
            throw new BusinessRuleException("تم تسليم هذا الاختبار بالفعل");
        }
        return grade(exam, student, existing, request);
    }

    /**
     * The offline-sync submit path (replayed from the outbox). Runs in its OWN
     * transaction so a rejection can never mark the push batch's transaction
     * rollback-only, and is idempotent: an already-submitted attempt returns its
     * existing result instead of throwing, so a replay is always safe.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StudentExamResult submitOffline(UUID examId, UUID studentUserId, StudentExamSubmitRequest request) {
        Student student = requireStudent(studentUserId);
        Exam exam = requireEligibleExam(examId, student.getGroup());
        ExamAttempt existing = attemptRepository.findByExamIdAndStudentId(examId, student.getId()).orElse(null);
        if (existing != null && "submitted".equals(existing.getStatus())) {
            return result(examId, studentUserId);
        }
        return grade(exam, student, existing, request);
    }

    /** Grade a submission, persist the attempt + answers, record the lesson score and notify parents. */
    private StudentExamResult grade(Exam exam, Student student, ExamAttempt existing, StudentExamSubmitRequest request) {
        // Index the student's selections by question.
        Map<UUID, Set<UUID>> selectedByQuestion = new java.util.HashMap<>();
        if (request.answers() != null) {
            for (StudentAnswerInput a : request.answers()) {
                if (a.questionId() != null) {
                    selectedByQuestion.put(a.questionId(),
                            a.choiceIds() == null ? Set.of() : new HashSet<>(a.choiceIds()));
                }
            }
        }

        List<ExamQuestion> questions = questionRepository.findByExamIdOrderByPositionAsc(exam.getId());
        Map<UUID, List<ExamChoice>> choicesByQuestion = choicesByQuestion(questions);

        ExamAttempt attempt = existing != null ? existing : new ExamAttempt();
        attempt.setExamId(exam.getId());
        attempt.setStudentId(student.getId());
        attempt.setStartedAt(request.startedAt() != null ? request.startedAt() : OffsetDateTime.now());
        attempt.setMaxScore(exam.getMaxScore());
        attempt.setStatus("in_progress");
        attemptRepository.saveAndFlush(attempt);

        // Fresh answer set for this attempt.
        answerRepository.deleteAll(answerRepository.findByAttemptId(attempt.getId()));

        BigDecimal scoreTotal = BigDecimal.ZERO;
        BigDecimal bonusTotal = BigDecimal.ZERO;
        List<StudentExamQuestionResult> results = new ArrayList<>();

        for (ExamQuestion q : questions) {
            List<ExamChoice> choices = choicesByQuestion.getOrDefault(q.getId(), List.of());
            Set<UUID> correctIds = choices.stream().filter(ExamChoice::isCorrect)
                    .map(ExamChoice::getId).collect(Collectors.toSet());
            Set<UUID> selected = selectedByQuestion.getOrDefault(q.getId(), Set.of());

            // Correct when the selection matches the correct set exactly (works for
            // single- and multi-answer; an empty correct set can never be matched).
            boolean correct = !correctIds.isEmpty() && selected.equals(correctIds);
            BigDecimal awarded = BigDecimal.ZERO;
            if (correct) {
                if (q.isBonus()) {
                    BigDecimal b = q.getBonusScore() == null ? BigDecimal.ZERO : q.getBonusScore();
                    bonusTotal = bonusTotal.add(b);
                    awarded = b;
                } else {
                    BigDecimal s = q.getScore() == null ? BigDecimal.ZERO : q.getScore();
                    scoreTotal = scoreTotal.add(s);
                    awarded = s;
                }
            }

            ExamAnswer answer = new ExamAnswer();
            answer.setAttemptId(attempt.getId());
            answer.setQuestionId(q.getId());
            answer.setChoiceIds(selected.toArray(UUID[]::new));
            answer.setCorrect(correct);
            answer.setAwarded(awarded);
            answerRepository.save(answer);

            results.add(new StudentExamQuestionResult(
                    q.getId(), q.getText(), q.getScore(), q.isBonus(), q.getBonusScore(), correct, awarded,
                    new ArrayList<>(selected), new ArrayList<>(correctIds), choiceViews(choices)));
        }

        attempt.setScore(scoreTotal);
        attempt.setBonusScore(bonusTotal);
        attempt.setSubmittedAt(OffsetDateTime.now());
        attempt.setStatus("submitted");
        attemptRepository.save(attempt);

        recordLessonScore(exam, student, scoreTotal);
        notifyParents(exam, student, scoreTotal, bonusTotal);

        return new StudentExamResult(exam.getId(), exam.getName(), scoreTotal, bonusTotal,
                exam.getMaxScore(), attempt.getSubmittedAt(), results);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentExamResult result(UUID examId, UUID studentUserId) {
        Student student = requireStudent(studentUserId);
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException(EXAM_NOT_AVAILABLE));
        ExamAttempt attempt = attemptRepository.findByExamIdAndStudentId(examId, student.getId())
                .filter(a -> "submitted".equals(a.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("لا توجد نتيجة لهذا الاختبار"));

        Map<UUID, ExamAnswer> answers = answerRepository.findByAttemptId(attempt.getId()).stream()
                .collect(Collectors.toMap(ExamAnswer::getQuestionId, a -> a));
        List<ExamQuestion> questions = questionRepository.findByExamIdOrderByPositionAsc(examId);
        Map<UUID, List<ExamChoice>> choicesByQuestion = choicesByQuestion(questions);

        List<StudentExamQuestionResult> results = new ArrayList<>();
        for (ExamQuestion q : questions) {
            List<ExamChoice> choices = choicesByQuestion.getOrDefault(q.getId(), List.of());
            List<UUID> correctIds = choices.stream().filter(ExamChoice::isCorrect).map(ExamChoice::getId).toList();
            ExamAnswer answer = answers.get(q.getId());
            List<UUID> selected = answer == null ? List.of() : Arrays.asList(answer.getChoiceIds());
            results.add(new StudentExamQuestionResult(
                    q.getId(), q.getText(), q.getScore(), q.isBonus(), q.getBonusScore(),
                    answer != null && answer.isCorrect(), answer == null ? BigDecimal.ZERO : answer.getAwarded(),
                    selected, correctIds, choiceViews(choices)));
        }

        return new StudentExamResult(exam.getId(), exam.getName(), attempt.getScore(), attempt.getBonusScore(),
                attempt.getMaxScore(), attempt.getSubmittedAt(), results);
    }

    // --- Lesson integration ----------------------------------------------

    /** Write the achieved score onto the student's registration for the lesson. */
    private void recordLessonScore(Exam exam, Student student, BigDecimal score) {
        Lecture lecture = lectureRepository.findById(exam.getLectureId()).orElse(null);
        if (lecture == null) {
            return;
        }
        Registration registration = registrationRepository
                .findByLectureIdAndStudentId(lecture.getId(), student.getId()).orElse(null);
        if (registration == null) {
            registration = new Registration();
            registration.setLecture(lecture);
            registration.setStudent(student);
            registration.setGroup(student.getGroup());
            registration.setStatus(RegistrationStatus.PRESENT);
            registration.setExamScore(score);
            registrationRepository.saveAndFlush(registration);
            if (student.getGroup() != null) {
                attendanceRepository.logToday(student.getGroup().getId(), student.getId(), TenantContext.get());
            }
        } else {
            registration.setExamScore(score);
            registrationRepository.saveAndFlush(registration);
        }
    }

    // --- Parent notifications --------------------------------------------

    private void notifyParents(Exam exam, Student student, BigDecimal score, BigDecimal bonus) {
        String teacher = teacherName();
        String max = plain(exam.getMaxScore());
        String bonusPart = bonus != null && bonus.signum() > 0 ? " (+" + plain(bonus) + " بونص)" : "";
        String title = "نتيجة اختبار";
        String body = templateService.render("exam_result", java.util.Map.of(
                "student.name", student.getName(),
                "exam.score", plain(score),
                "exam.max", max,
                "exam.bonus", bonusPart,
                "exam.name", exam.getName())).body();

        for (ParentStudentLink link : parentLinkRepository.findByStudentIdAndStatus(student.getId(), LinkStatus.APPROVED)) {
            Parent parent = parentRepository.findById(link.getParentId()).orElse(null);
            if (parent == null) {
                continue;
            }
            if (parent.getUserId() != null) {
                notificationService.notifyFrom(parent.getUserId(), TenantContext.get(), teacher,
                        NotificationType.EXAM_GRADED, title, body, exam.getId(), null);
            }
            if (parent.getPhone() != null && !parent.getPhone().isBlank()) {
                try {
                    greenApiClient.sendText("exam_result", parent.getPhone(), body);
                } catch (RuntimeException ex) {
                    // A failed WhatsApp must not roll back the graded attempt.
                    log.warn("Exam result WhatsApp to parent {} failed: {}", parent.getId(), ex.getMessage());
                }
            }
        }
    }

    // --- Helpers ----------------------------------------------------------

    private Student requireStudent(UUID studentUserId) {
        return studentRepository.findByUserId(studentUserId)
                .orElseThrow(() -> new ResourceNotFoundException(STUDENT_NOT_FOUND));
    }

    private Exam requireEligibleExam(UUID examId, Group group) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException(EXAM_NOT_AVAILABLE));
        if (!isEligible(exam, group)) {
            throw new ResourceNotFoundException(EXAM_NOT_AVAILABLE);
        }
        return exam;
    }

    private boolean isEligible(Exam exam, Group group) {
        return exam.getPublishedAt() != null
                && exam.getDeletedAt() == null
                && group != null
                && exam.getGroupIds() != null
                && Arrays.asList(exam.getGroupIds()).contains(group.getId());
    }

    private OffsetDateTime availableUntil(Exam exam, Group group) {
        if (exam.getScheduledDate() == null || group == null || group.getStartTime() == null) {
            return null;
        }
        // The cutoff is the lesson's own date and time: the exam disappears exactly
        // when the lesson starts (e.g. Sunday 2:00 PM), with no grace window.
        return exam.getScheduledDate().atTime(group.getStartTime()).atZone(CAIRO).toOffsetDateTime();
    }

    private static String statusOf(ExamAttempt attempt) {
        return attempt == null ? "available" : attempt.getStatus();
    }

    /** The version currently live to students (set at each publish); 1 before any. */
    private static int liveVersion(Exam exam) {
        return exam.getPublishedVersion() == null ? 1 : exam.getPublishedVersion();
    }

    /** The password for this student's group; falls back to the legacy single one. */
    private String groupPassword(Exam exam, Group group) {
        if (group != null) {
            String p = groupPasswordRepository.findByExamIdAndGroupId(exam.getId(), group.getId())
                    .map(gp -> gp.getPassword()).orElse(null);
            if (p != null) {
                return p;
            }
        }
        return exam.getExamPassword();
    }

    private String lectureName(UUID lectureId) {
        return lectureRepository.findById(lectureId).map(Lecture::getName).orElse(null);
    }

    private List<StudentExamChoiceView> choiceViews(UUID questionId) {
        return choiceViews(choiceRepository.findByQuestionIdInOrderByPositionAsc(List.of(questionId)));
    }

    private static List<StudentExamChoiceView> choiceViews(List<ExamChoice> choices) {
        return choices.stream()
                .map(c -> new StudentExamChoiceView(c.getId(), c.getLabel(), c.getText(), c.isCorrect()))
                .toList();
    }

    private Map<UUID, List<ExamChoice>> choicesByQuestion(List<ExamQuestion> questions) {
        List<UUID> ids = questions.stream().map(ExamQuestion::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return choiceRepository.findByQuestionIdInOrderByPositionAsc(ids).stream()
                .collect(Collectors.groupingBy(ExamChoice::getQuestionId));
    }

    private String teacherName() {
        UUID adminId = TenantContext.get();
        return adminId == null ? NotificationType.SYSTEM_SENDER
                : userRepository.findById(adminId).map(u -> u.getUsername()).orElse(NotificationType.SYSTEM_SENDER);
    }

    private static String plain(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }
}
