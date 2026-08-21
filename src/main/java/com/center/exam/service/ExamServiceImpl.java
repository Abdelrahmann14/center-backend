package com.center.exam.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.exam.dto.ExamBuilderRequest;
import com.center.exam.dto.ExamChoiceInput;
import com.center.exam.dto.ExamQuestionInput;
import com.center.exam.dto.ExamRequest;
import com.center.exam.dto.ExamScheduleRequest;
import com.center.exam.dto.ExamChoiceResponse;
import com.center.exam.dto.ExamDetailResponse;
import com.center.exam.dto.ExamQuestionResponse;
import com.center.exam.dto.ExamResponse;
import com.center.exam.dto.GroupPasswordView;
import com.center.exam.entity.Exam;
import com.center.exam.entity.ExamChoice;
import com.center.exam.entity.ExamGroupPassword;
import com.center.exam.entity.ExamQuestion;
import com.center.lecture.entity.Lecture;
import com.center.student.entity.Student;
import com.center.common.enums.NotificationType;
import com.center.common.exception.BusinessRuleException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.exam.repository.ExamChoiceRepository;
import com.center.exam.repository.ExamGroupPasswordRepository;
import com.center.exam.repository.ExamQuestionRepository;
import com.center.exam.repository.ExamRepository;
import com.center.lecture.repository.LectureRepository;
import com.center.student.repository.StudentRepository;
import com.center.user.repository.UserRepository;
import com.center.exam.service.ExamService;
import com.center.notification.service.NotificationService;
import com.center.common.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExamServiceImpl implements ExamService {

    private static final String EXAM_NOT_FOUND = "الاختبار غير موجود";
    private static final String LECTURE_NOT_FOUND = "الحصة غير موجودة";

    /** Exam password alphabet: lowercase English letters and digits only. */
    private static final char[] PASSWORD_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final int PASSWORD_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ExamRepository examRepository;
    private final ExamQuestionRepository questionRepository;
    private final ExamChoiceRepository choiceRepository;
    private final ExamGroupPasswordRepository groupPasswordRepository;
    private final LectureRepository lectureRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public List<ExamResponse> list() {
        return examRepository.findByDeletedAtIsNullOrderByGradeAscCreatedAtAsc().stream()
                .map(exam -> toResponse(exam, lectureName(exam.getLectureId()),
                        questionRepository.countByExamId(exam.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ExamDetailResponse get(UUID examId) {
        Exam exam = findEntity(examId);
        return toDetail(exam, lectureName(exam.getLectureId()));
    }

    @Override
    @Transactional
    public ExamResponse create(ExamRequest request) {
        Lecture lecture = lectureRepository.findById(request.lectureId())
                .orElseThrow(() -> new ResourceNotFoundException(LECTURE_NOT_FOUND));

        Exam exam = new Exam();
        exam.setLectureId(lecture.getId());
        exam.setName(request.name().strip());
        exam.setGrade(lecture.getGrade()); // stage auto-copied from the lesson
        exam.setMaxScore(request.maxScore());
        exam.setDurationMinutes(request.durationMinutes());
        examRepository.save(exam);

        writeBackToLesson(lecture, exam);
        return toResponse(exam, lecture.getName(), 0);
    }

    @Override
    @Transactional
    public ExamResponse update(UUID examId, ExamRequest request) {
        Exam exam = findEntity(examId);
        exam.setName(request.name().strip());
        exam.setMaxScore(request.maxScore());
        exam.setDurationMinutes(request.durationMinutes());
        // Max score may have changed, which shifts whether the distribution matches.
        exam.setComplete(computeComplete(exam));
        exam.setContentVersion(exam.getContentVersion() + 1);
        examRepository.save(exam);

        Lecture lecture = lectureRepository.findById(exam.getLectureId()).orElse(null);
        if (lecture != null) {
            writeBackToLesson(lecture, exam);
        }
        return toResponse(exam, lecture == null ? null : lecture.getName(),
                questionRepository.countByExamId(examId));
    }

    @Override
    @Transactional
    public ExamDetailResponse saveQuestions(UUID examId, ExamBuilderRequest request) {
        Exam exam = findEntity(examId);

        // Persist the exam-level settings authored in the builder's left panel.
        exam.setLabelStyle(normalizeLabelStyle(request.labelStyle()));
        exam.setAllowMultipleCorrect(request.allowMultipleCorrect());
        exam.setNotesEnabled(request.notesEnabled());
        exam.setBonusEnabled(request.bonusEnabled());

        // Draft-friendly save: NEVER blocks. Drop blank questions and blank choices,
        // store whatever remains (even incomplete), then recompute publishability.
        List<CleanQuestion> clean = new ArrayList<>();
        for (ExamQuestionInput q : request.questions() == null ? List.<ExamQuestionInput>of() : request.questions()) {
            if (q.text() == null || q.text().isBlank()) {
                continue;
            }
            List<ExamChoiceInput> choices = (q.choices() == null ? List.<ExamChoiceInput>of() : q.choices()).stream()
                    .filter(c -> c.text() != null && !c.text().isBlank())
                    .toList();
            clean.add(new CleanQuestion(q, choices));
        }

        // Replace wholesale: drop the old tree (DB cascades choices), then rebuild.
        questionRepository.deleteByExamId(examId);
        questionRepository.flush();

        int qPos = 0;
        for (CleanQuestion cq : clean) {
            ExamQuestionInput in = cq.input();
            boolean isBonus = exam.isBonusEnabled() && in.bonus();
            ExamQuestion question = new ExamQuestion();
            question.setExamId(examId);
            question.setText(in.text().strip());
            question.setPosition(qPos++);
            question.setAllowMultiple(exam.isAllowMultipleCorrect() && in.allowMultiple());
            question.setBonus(isBonus);
            question.setScore(isBonus ? BigDecimal.ZERO : safeScore(in.score()));
            question.setBonusScore(isBonus ? in.bonusScore() : null);
            question.setNote(exam.isNotesEnabled() && in.note() != null && !in.note().isBlank()
                    ? in.note().strip() : null);
            questionRepository.save(question);

            int cPos = 0;
            for (ExamChoiceInput c : cq.choices()) {
                ExamChoice choice = new ExamChoice();
                choice.setQuestionId(question.getId());
                choice.setLabel(c.label() == null ? "" : c.label().strip());
                choice.setText(c.text().strip());
                choice.setCorrect(c.correct());
                choice.setPosition(cPos++);
                choiceRepository.save(choice);
            }
        }
        choiceRepository.flush();
        exam.setComplete(computeComplete(exam));
        exam.setContentVersion(exam.getContentVersion() + 1);
        examRepository.save(exam);
        return toDetail(exam, lectureName(exam.getLectureId()));
    }

    @Override
    @Transactional
    public ExamResponse schedule(UUID examId, ExamScheduleRequest request) {
        // An exam can never be scheduled in the past.
        if (request.date().isBefore(LocalDate.now())) {
            throw new BusinessRuleException("لا يمكن جدولة الاختبار في تاريخ سابق");
        }
        Exam exam = findEntity(examId);
        exam.setScheduledDate(request.date());
        exam.setGroupIds(request.groupIds().toArray(UUID[]::new));
        exam.setContentVersion(exam.getContentVersion() + 1);
        examRepository.save(exam);
        return toResponse(exam, lectureName(exam.getLectureId()),
                questionRepository.countByExamId(examId));
    }

    @Override
    @Transactional
    public ExamResponse publish(UUID examId) {
        Exam exam = findEntity(examId);
        if (!exam.isComplete()) {
            throw new BusinessRuleException("لا يمكن نشر اختبار غير مكتمل");
        }
        if (exam.getScheduledDate() == null || exam.getGroupIds() == null || exam.getGroupIds().length == 0) {
            throw new BusinessRuleException("يجب جدولة الاختبار لمجموعة وتاريخ قبل النشر");
        }
        if (exam.getPublishedAt() == null) {
            exam.setPublishedAt(OffsetDateTime.now());
        }
        // Every publish makes the current content live and regenerates each group's
        // password, so a fresh secret is handed out on every re-publish.
        exam.setPublishedVersion(exam.getContentVersion());
        examRepository.save(exam);
        regenerateGroupPasswords(exam);

        return toResponse(exam, lectureName(exam.getLectureId()), questionRepository.countByExamId(examId));
    }

    @Override
    @Transactional
    public void delete(UUID examId) {
        Exam exam = findEntity(examId);
        examRepository.delete(exam); // FK cascade removes its questions, choices and passwords
    }

    // --- Helpers ----------------------------------------------------------

    private Exam findEntity(UUID examId) {
        return examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException(EXAM_NOT_FOUND));
    }

    private String lectureName(UUID lectureId) {
        return lectureRepository.findById(lectureId).map(Lecture::getName).orElse(null);
    }

    /**
     * Two-way sync: the exam's name and max score are written back onto the linked
     * lesson's {@code examName}/{@code examGrade}, so both always match and the
     * assistant's score validation uses the same cap.
     */
    private void writeBackToLesson(Lecture lecture, Exam exam) {
        lecture.setExamName(exam.getName());
        lecture.setExamGrade(plainScore(exam.getMaxScore()));
        lectureRepository.save(lecture);
    }

    private static String plainScore(BigDecimal score) {
        return score == null ? null : score.stripTrailingZeros().toPlainString();
    }

    private static String normalizeLabelStyle(String style) {
        return "english".equalsIgnoreCase(style) ? "english" : "arabic";
    }

    private static final BigDecimal TWO = new BigDecimal(2);

    /** A positive value that is a whole number or ends in .5 (no finer precision). */
    private static boolean isHalfStep(BigDecimal v) {
        return v != null && v.signum() > 0 && v.multiply(TWO).stripTrailingZeros().scale() <= 0;
    }

    /** Draft scores may be missing; store 0 so the column stays non-null. */
    private static BigDecimal safeScore(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** A fixed 10-char password of lowercase English letters and digits. */
    private static String generatePassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_ALPHABET[RANDOM.nextInt(PASSWORD_ALPHABET.length)]);
        }
        return sb.toString();
    }

    /** Replace every assigned group's password with a fresh, distinct one. */
    private void regenerateGroupPasswords(Exam exam) {
        groupPasswordRepository.deleteByExamId(exam.getId());
        groupPasswordRepository.flush();
        if (exam.getGroupIds() == null) {
            return;
        }
        for (UUID groupId : exam.getGroupIds()) {
            groupPasswordRepository.save(new ExamGroupPassword(exam.getId(), groupId, generatePassword()));
        }
    }

    /** The teacher (workspace admin) display name, used as the notification sender. */
    private String teacherName() {
        UUID adminId = TenantContext.get();
        return adminId == null ? NotificationType.SYSTEM_SENDER
                : userRepository.findById(adminId).map(u -> u.getUsername()).orElse(NotificationType.SYSTEM_SENDER);
    }

    /**
     * Whether the exam passes every publish rule: at least one question, each with
     * ≥2 choices and a valid correct-answer count, valid per-question scores, and
     * the regular (non-bonus) scores summing exactly to the max score.
     */
    private boolean computeComplete(Exam exam) {
        if (exam.getMaxScore() == null) {
            return false;
        }
        List<ExamQuestion> questions = questionRepository.findByExamIdOrderByPositionAsc(exam.getId());
        if (questions.isEmpty()) {
            return false;
        }
        List<UUID> ids = questions.stream().map(ExamQuestion::getId).toList();
        Map<UUID, List<ExamChoice>> byQuestion = choiceRepository.findByQuestionIdInOrderByPositionAsc(ids).stream()
                .collect(Collectors.groupingBy(ExamChoice::getQuestionId));

        BigDecimal regularTotal = BigDecimal.ZERO;
        for (ExamQuestion q : questions) {
            List<ExamChoice> choices = byQuestion.getOrDefault(q.getId(), List.of());
            if (choices.size() < 2) {
                return false;
            }
            long correct = choices.stream().filter(ExamChoice::isCorrect).count();
            boolean multi = exam.isAllowMultipleCorrect() && q.isAllowMultiple();
            if (multi ? correct < 1 : correct != 1) {
                return false;
            }
            if (q.isBonus()) {
                if (!isHalfStep(q.getBonusScore())) {
                    return false;
                }
            } else if (!isHalfStep(q.getScore())) {
                return false;
            } else {
                regularTotal = regularTotal.add(q.getScore());
            }
        }
        return regularTotal.compareTo(exam.getMaxScore()) == 0;
    }

    /** A cleaned question paired with its non-blank choices, ready to validate/save. */
    private record CleanQuestion(ExamQuestionInput input, List<ExamChoiceInput> choices) {
    }

    private ExamResponse toResponse(Exam exam, String lectureName, long questionCount) {
        return new ExamResponse(
                exam.getId(), exam.getLectureId(), lectureName, exam.getName(), exam.getGrade(),
                exam.getMaxScore(), exam.getDurationMinutes(), exam.getScheduledDate(),
                groupIds(exam), exam.getLabelStyle(), exam.isAllowMultipleCorrect(),
                exam.isNotesEnabled(), exam.isBonusEnabled(), exam.isComplete(),
                exam.getExamPassword(), groupPasswordViews(exam.getId()),
                exam.getPublishedAt() != null, questionCount);
    }

    private List<GroupPasswordView> groupPasswordViews(UUID examId) {
        return groupPasswordRepository.findByExamId(examId).stream()
                .map(p -> new GroupPasswordView(p.getGroupId(), p.getPassword()))
                .toList();
    }

    private ExamDetailResponse toDetail(Exam exam, String lectureName) {
        List<ExamQuestion> questions = questionRepository.findByExamIdOrderByPositionAsc(exam.getId());
        List<UUID> questionIds = questions.stream().map(ExamQuestion::getId).toList();
        Map<UUID, List<ExamChoice>> choicesByQuestion = questionIds.isEmpty()
                ? Map.of()
                : choiceRepository.findByQuestionIdInOrderByPositionAsc(questionIds).stream()
                        .collect(Collectors.groupingBy(ExamChoice::getQuestionId));

        List<ExamQuestionResponse> questionResponses = new ArrayList<>();
        for (ExamQuestion q : questions) {
            List<ExamChoiceResponse> choices = choicesByQuestion.getOrDefault(q.getId(), List.of()).stream()
                    .map(c -> new ExamChoiceResponse(c.getId(), c.getLabel(), c.getText(), c.isCorrect(), c.getPosition()))
                    .toList();
            questionResponses.add(new ExamQuestionResponse(q.getId(), q.getText(), q.getPosition(),
                    q.getScore(), q.isAllowMultiple(), q.isBonus(), q.getBonusScore(), q.getNote(), choices));
        }

        return new ExamDetailResponse(
                exam.getId(), exam.getLectureId(), lectureName, exam.getName(), exam.getGrade(),
                exam.getMaxScore(), exam.getDurationMinutes(), exam.getScheduledDate(),
                groupIds(exam), exam.getLabelStyle(), exam.isAllowMultipleCorrect(),
                exam.isNotesEnabled(), exam.isBonusEnabled(), exam.isComplete(),
                exam.getExamPassword(), groupPasswordViews(exam.getId()),
                exam.getPublishedAt() != null, questionResponses);
    }

    private static List<UUID> groupIds(Exam exam) {
        return exam.getGroupIds() == null ? List.of() : Arrays.asList(exam.getGroupIds());
    }
}
