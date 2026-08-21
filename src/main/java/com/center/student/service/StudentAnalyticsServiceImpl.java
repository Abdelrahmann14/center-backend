package com.center.student.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.common.enums.RegistrationStatus;
import com.center.common.exception.ResourceNotFoundException;
import com.center.group.entity.Group;
import com.center.registration.entity.Registration;
import com.center.registration.repository.RegistrationRepository;
import com.center.student.dto.StudentAnalyticsResponse;
import com.center.student.dto.StudentAnalyticsResponse.Entry;
import com.center.student.dto.StudentAnalyticsResponse.Summary;
import com.center.student.repository.StudentRepository;

import lombok.RequiredArgsConstructor;

/**
 * Builds a student's academic history from their lesson registrations.
 *
 * <p>Tracking deliberately starts at the student's first present row: a student
 * who joined late is not judged on lessons held before they existed, and a
 * student who never attended has no history at all.
 */
@Service
@RequiredArgsConstructor
public class StudentAnalyticsServiceImpl implements StudentAnalyticsService {

    private static final String[] DAY_NAMES = {
            "السبت", "الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة"
    };
    /** The exam cap is free text ("50", "50 درجة"); its first number is the max. */
    private static final Pattern FIRST_NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private final StudentRepository studentRepository;
    private final RegistrationRepository registrationRepository;

    private static final StudentAnalyticsResponse EMPTY =
            new StudentAnalyticsResponse(false, null, List.of());

    @Override
    @Transactional(readOnly = true)
    public StudentAnalyticsResponse analytics(UUID studentId) {
        // Reads the student first so an unknown id 404s instead of looking empty.
        studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("الطالب غير موجود"));

        List<Registration> history = registrationRepository.findStudentHistory(studentId);
        List<Registration> attended = history.stream()
                .filter(r -> r.getStatus() == RegistrationStatus.PRESENT)
                .toList();
        if (attended.isEmpty()) {
            return EMPTY;
        }

        OffsetDateTime firstAt = attended.get(0).getCreatedAt();

        List<Entry> timeline = new ArrayList<>();
        Set<UUID> attendedLectures = new LinkedHashSet<>();
        for (Registration r : attended) {
            attendedLectures.add(r.getLecture().getId());
            timeline.add(attendedEntry(r));
        }

        // Every group this student has ever registered under, so a student who
        // moved groups is measured against the lessons they could have attended.
        Set<UUID> groupIds = new LinkedHashSet<>();
        for (Registration r : history) {
            if (r.getGroup() != null) {
                groupIds.add(r.getGroup().getId());
            }
        }
        for (RegistrationRepository.GroupLessonRow held : groupLessons(groupIds, firstAt)) {
            if (attendedLectures.contains(held.getLectureId())) {
                continue;
            }
            timeline.add(new Entry(held.getLectureId(), held.getLectureName(),
                    toDate(held.getHeldAt()), null, null, false,
                    null, held.getHasExam(), false, null, null, null, null));
        }

        timeline.sort(Comparator.comparing(Entry::date, Comparator.nullsLast(Comparator.naturalOrder())));
        return new StudentAnalyticsResponse(true, summarise(timeline), timeline);
    }

    private List<RegistrationRepository.GroupLessonRow> groupLessons(Set<UUID> groupIds, OffsetDateTime since) {
        // `in ()` is invalid SQL, so a student with no group has nothing to miss.
        return groupIds.isEmpty()
                ? List.of()
                : registrationRepository.findGroupLessonsSince(groupIds, RegistrationStatus.PRESENT, since);
    }

    private Entry attendedEntry(Registration r) {
        // The score typed on the lesson register is the only one there is.
        BigDecimal score = null;
        BigDecimal max = null;
        String examName = r.getLecture().getExamName();
        boolean taken = false;
        if (r.getExamScore() != null) {
            score = r.getExamScore();
            max = parseMax(r.getLecture().getExamGrade());
            taken = true;
        }
        return new Entry(
                r.getLecture().getId(),
                r.getLecture().getName(),
                toDate(r.getCreatedAt()),
                r.getCreatedAt(),
                groupLabel(r.getGroup()),
                true,
                examName,
                r.getLecture().isHasExam(),
                taken,
                score,
                max,
                percent(score, max),
                r.getHomeworkFlag() == null ? null : r.getHomeworkFlag().getValue());
    }

    private Summary summarise(List<Entry> timeline) {
        long attended = timeline.stream().filter(Entry::attended).count();
        long missed = timeline.size() - attended;
        long examsTaken = timeline.stream().filter(Entry::examTaken).count();
        // An exam counts as missed only when the lesson had one and the student
        // has no score for it - a lesson without an exam is not a miss.
        // The lesson now SAYS whether it had an exam, instead of this being
        // inferred from whether someone happened to type an exam name.
        long examsMissed = timeline.stream()
                .filter(e -> e.hasExam() && !e.examTaken())
                .count();

        List<BigDecimal> percents = timeline.stream()
                .map(Entry::examPercent)
                .filter(p -> p != null)
                .toList();
        BigDecimal average = null;
        BigDecimal best = null;
        BigDecimal worst = null;
        if (!percents.isEmpty()) {
            BigDecimal total = percents.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            average = total.divide(BigDecimal.valueOf(percents.size()), 1, RoundingMode.HALF_UP);
            best = percents.stream().max(Comparator.naturalOrder()).orElse(null);
            worst = percents.stream().min(Comparator.naturalOrder()).orElse(null);
        }

        long homeworkIssues = timeline.stream().filter(e -> e.homeworkFlag() != null).count();

        long current = 0;
        long longest = 0;
        long run = 0;
        for (Entry e : timeline) {
            run = e.attended() ? run + 1 : 0;
            longest = Math.max(longest, run);
        }
        current = run;

        LocalDate first = timeline.stream().filter(Entry::attended)
                .map(Entry::date).min(Comparator.naturalOrder()).orElse(null);
        LocalDate last = timeline.stream().filter(Entry::attended)
                .map(Entry::date).max(Comparator.naturalOrder()).orElse(null);

        return new Summary(first, last, attended, missed,
                percent(BigDecimal.valueOf(attended), BigDecimal.valueOf(attended + missed)),
                examsTaken, examsMissed, average, best, worst,
                homeworkIssues, current, longest);
    }

    /** Matches the web app's group label: "الخميس · 18:00 · ilearn". */
    private static String groupLabel(Group g) {
        if (g == null) {
            return null;
        }
        String day = g.getDayOfWeek() >= 0 && g.getDayOfWeek() < DAY_NAMES.length
                ? DAY_NAMES[g.getDayOfWeek()]
                : "";
        // getStartTime() straight into a string prints "16:00:00" - the machine's
        // spelling, in a label a teacher reads.
        return "%s · %s · %s".formatted(day,
                com.center.common.util.ArabicFormat.time(g.getStartTime()), g.getCenterName());
    }

    private static BigDecimal parseMax(String examGrade) {
        if (examGrade == null) {
            return null;
        }
        Matcher m = FIRST_NUMBER.matcher(examGrade);
        return m.find() ? new BigDecimal(m.group()) : null;
    }

    private static BigDecimal percent(BigDecimal value, BigDecimal max) {
        if (value == null || max == null || max.signum() == 0) {
            return null;
        }
        return value.multiply(BigDecimal.valueOf(100)).divide(max, 1, RoundingMode.HALF_UP);
    }

    private static LocalDate toDate(OffsetDateTime at) {
        return at == null ? null : at.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
    }
}
