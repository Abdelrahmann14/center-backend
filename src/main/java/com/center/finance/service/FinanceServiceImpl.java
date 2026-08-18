package com.center.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.common.enums.FinanceEntryKind;
import com.center.common.enums.RegistrationStatus;
import com.center.common.enums.Role;
import com.center.common.exception.BusinessRuleException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.common.tenant.TenantContext;
import com.center.finance.dto.AssistantAttendanceRecordResponse;
import com.center.finance.dto.AssistantAttendanceResponse;
import com.center.finance.dto.AttendanceRequest;
import com.center.finance.dto.FinanceEntryRequest;
import com.center.finance.dto.FinanceEntryResponse;
import com.center.finance.dto.InvoiceLineResponse;
import com.center.finance.dto.InvoiceResponse;
import com.center.finance.entity.FinanceEntry;
import com.center.finance.entity.LessonAttendance;
import com.center.finance.repository.FinanceEntryRepository;
import com.center.finance.repository.LessonAttendanceRepository;
import com.center.group.entity.Group;
import com.center.group.repository.GroupRepository;
import com.center.lecture.entity.Lecture;
import com.center.lecture.repository.LectureRepository;
import com.center.registration.repository.RegistrationRepository;
import com.center.registration.repository.RegistrationRepository.SessionPriceRow;
import com.center.user.entity.User;
import com.center.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Builds a lesson's invoice from what the desk already recorded.
 *
 * <p>Nothing here is stored: an invoice is derived on every read from the
 * registrations, the center's price list and the manual lines. That is
 * deliberate - a stored total would drift the moment a student's price or a
 * center's share was corrected, and the teacher would have no way to tell which
 * number was the true one.
 *
 * <p>Every money value is rounded UP to whole pounds at the point it is produced,
 * so what the invoice shows is what the arithmetic used. Rounding only at the end
 * would let a line read 60 while contributing 59.5 to the total.
 */
@Service
@RequiredArgsConstructor
public class FinanceServiceImpl implements FinanceService {

    private static final String ENTRY_NOT_FOUND = "البند غير موجود";
    private static final String INVOICE_NOT_FOUND = "لا توجد فاتورة لهذه الحصة";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** 0 = Saturday, matching groups.day_of_week. */
    private static final String[] DAYS = {"السبت", "الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة"};

    private final RegistrationRepository registrationRepository;
    private final FinanceEntryRepository financeEntryRepository;
    private final LessonAttendanceRepository lessonAttendanceRepository;
    private final LectureRepository lectureRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;

    /**
     * Widest window one request may ask for.
     *
     * <p>An invoice is derived, not stored, so this endpoint reads every PRESENT
     * registration in the window and buckets them in memory. Unbounded,
     * {@code from=2000-01-01&to=2099-12-31} is a perfectly legal request that
     * pulls the workspace's entire attendance history into heap and holds a
     * pooled connection while it does - available to any signed-in assistant. A
     * year covers every real use of the page (it is browsed a month at a time)
     * and refuses the pathological one.
     */
    private static final int MAX_INVOICE_DAYS = 366;

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceResponse> invoices(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessRuleException("تاريخ النهاية قبل تاريخ البداية");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_INVOICE_DAYS) {
            throw new BusinessRuleException("المدة المطلوبة أطول من سنة - اختر فترة أقصر");
        }
        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime start = from.atStartOfDay(zone).toOffsetDateTime();
        // Exclusive upper bound on the day after `to`, so the last day is whole.
        OffsetDateTime until = to.plusDays(1).atStartOfDay(zone).toOffsetDateTime();

        List<SessionPriceRow> rows =
                registrationRepository.findSessionPriceRows(RegistrationStatus.PRESENT, start, until);
        if (rows.isEmpty()) {
            return List.of();
        }

        // One session per (lecture, group, day), each holding a head count per
        // price. The day is derived here, in the application's zone, so a late
        // evening lesson is filed under the day it was actually taught.
        Map<String, Session> sessions = new LinkedHashMap<>();
        for (SessionPriceRow row : rows) {
            LocalDate day = row.getRegisteredAt().atZoneSameInstant(zone).toLocalDate();
            sessions.computeIfAbsent(key(row.getLectureId(), row.getGroupId(), day),
                            k -> new Session(row.getLectureId(), row.getGroupId(), day))
                    .add(row.getPrice());
        }

        Map<UUID, Lecture> lectures = byId(lectureRepository.findAllById(
                rows.stream().map(SessionPriceRow::getLectureId).collect(Collectors.toSet())),
                Lecture::getId);
        Set<UUID> groupIds = rows.stream()
                .map(SessionPriceRow::getGroupId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Group> groups = byId(groupRepository.findAllById(groupIds), Group::getId);

        Map<String, List<FinanceEntry>> entries = new HashMap<>();
        for (FinanceEntry entry : financeEntryRepository.findBySessionDateBetweenOrderByCreatedAtAsc(from, to)) {
            entries.computeIfAbsent(key(entry.getLectureId(), entry.getGroupId(), entry.getSessionDate()),
                    k -> new ArrayList<>()).add(entry);
        }

        Map<String, List<String>> attendees = attendeesBySession(from, to);

        List<InvoiceResponse> invoices = new ArrayList<>(sessions.size());
        for (var session : sessions.entrySet()) {
            Session s = session.getValue();
            invoices.add(build(
                    session.getKey(),
                    lectures.get(s.lectureId),
                    s.lectureId,
                    s.groupId == null ? null : groups.get(s.groupId),
                    s.groupId,
                    s.day,
                    s,
                    entries.getOrDefault(session.getKey(), List.of()),
                    attendees.getOrDefault(session.getKey(), List.of())));
        }

        // Newest session first, then the earliest group slot within a day - the
        // page reads as a diary, most recent at the top.
        invoices.sort(Comparator.comparing(InvoiceResponse::sessionDate).reversed()
                .thenComparing(i -> i.startTime() == null ? LocalTime.MIDNIGHT : i.startTime()));
        return invoices;
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse invoice(UUID lectureId, UUID groupId, LocalDate sessionDate) {
        String wanted = key(lectureId, groupId, sessionDate);
        return invoices(sessionDate, sessionDate).stream()
                .filter(i -> i.key().equals(wanted))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(INVOICE_NOT_FOUND));
    }

    @Override
    @Transactional
    public FinanceEntryResponse addEntry(FinanceEntryRequest request) {
        return toResponse(financeEntryRepository.save(apply(new FinanceEntry(), request)));
    }

    @Override
    @Transactional
    public FinanceEntryResponse updateEntry(UUID entryId, FinanceEntryRequest request) {
        FinanceEntry entry = financeEntryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTRY_NOT_FOUND));
        return toResponse(financeEntryRepository.save(apply(entry, request)));
    }

    @Override
    @Transactional
    public FinanceEntryResponse upsertEntry(UUID entryId, FinanceEntryRequest request) {
        FinanceEntry entry = financeEntryRepository.findById(entryId).orElseGet(() -> {
            FinanceEntry fresh = new FinanceEntry();
            fresh.setId(entryId);
            return fresh;
        });
        return toResponse(financeEntryRepository.save(apply(entry, request)));
    }

    @Override
    // noRollbackFor: idempotent sync delete catches "already gone"; the RNF is
    // pre-write (see RegistrationServiceImpl.unregister).
    @Transactional(noRollbackFor = ResourceNotFoundException.class)
    public void deleteEntry(UUID entryId) {
        if (!financeEntryRepository.existsById(entryId)) {
            throw new ResourceNotFoundException(ENTRY_NOT_FOUND);
        }
        financeEntryRepository.deleteById(entryId);
    }

    // ── Assembly ────────────────────────────────────────────────────────────

    /**
     * One lesson session while it is being assembled: who it belongs to, and how
     * many present students sit at each price. The price map is a TreeMap so the
     * buckets come out in a stable order regardless of registration order; the
     * no-price bucket is held apart because null cannot be a TreeMap key.
     */
    private static final class Session {
        private final UUID lectureId;
        private final UUID groupId;
        private final LocalDate day;
        private final TreeMap<BigDecimal, Long> heads = new TreeMap<>();
        private long noPrice;

        private Session(UUID lectureId, UUID groupId, LocalDate day) {
            this.lectureId = lectureId;
            this.groupId = groupId;
            this.day = day;
        }

        private void add(BigDecimal price) {
            if (price == null) {
                noPrice++;
            } else {
                // Strip the scale so 50 and 50.00 are one bucket, not two.
                heads.merge(price.stripTrailingZeros(), 1L, Long::sum);
            }
        }
    }

    private InvoiceResponse build(String key, Lecture lecture, UUID lectureId, Group group, UUID groupId,
            LocalDate sessionDate, Session session, List<FinanceEntry> entries, List<String> attendees) {

        BigDecimal official = group == null || group.getLessonPrice() == null
                ? BigDecimal.ZERO
                : group.getLessonPrice();
        BigDecimal percentage = group == null || group.getCenterPercentage() == null
                ? BigDecimal.ZERO
                : group.getCenterPercentage();

        List<InvoiceLineResponse> lines = new ArrayList<>(session.heads.size() + 1);
        BigDecimal gross = BigDecimal.ZERO;
        long attended = 0;
        // Dearest first: the full-price line is the baseline every discount below
        // it is read against. The TreeMap is ascending, so walk it backwards.
        for (var bucket : session.heads.descendingMap().entrySet()) {
            BigDecimal price = bucket.getKey();
            long count = bucket.getValue();
            BigDecimal subtotal = up(price.multiply(BigDecimal.valueOf(count)));
            lines.add(new InvoiceLineResponse(price, count, subtotal, price.compareTo(official) < 0));
            gross = gross.add(subtotal);
            attended += count;
        }
        if (session.noPrice > 0) {
            // A student with no price set contributes nothing but still attended;
            // dropping them from the head count would misstate the invoice. Last,
            // because it is an omission rather than a rate.
            lines.add(new InvoiceLineResponse(null, session.noPrice, BigDecimal.ZERO, true));
            attended += session.noPrice;
        }

        BigDecimal centerCut = up(gross.multiply(percentage).divide(HUNDRED, 6, RoundingMode.HALF_UP));
        BigDecimal netAfterCut = gross.subtract(centerCut);

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        List<FinanceEntryResponse> lineItems = new ArrayList<>(entries.size());
        for (FinanceEntry entry : entries) {
            BigDecimal amount = up(entry.getAmount());
            if (entry.getKind() == FinanceEntryKind.INCOME) {
                income = income.add(amount);
            } else {
                expense = expense.add(amount);
            }
            lineItems.add(toResponse(entry));
        }

        return new InvoiceResponse(
                key,
                lectureId,
                lecture == null ? "حصة محذوفة" : lecture.getName(),
                groupId,
                groupLabel(group),
                group == null ? null : group.getCenterName(),
                group != null ? group.getGrade() : lecture == null ? null : lecture.getGrade(),
                sessionDate,
                group == null ? null : group.getStartTime(),
                group == null ? attended : group.getStudentCount(),
                attended,
                up(official),
                lines,
                gross,
                percentage,
                centerCut,
                netAfterCut,
                lineItems,
                income,
                expense,
                netAfterCut.add(income).subtract(expense),
                attendees);
    }

    // ── Assistant attendance ──────────────────────────────────────────────────

    /**
     * Assistant names present at each session in the window, keyed by session, for
     * the invoice card and PDF. A row whose assistant was later deleted is dropped
     * rather than shown as a blank.
     */
    private Map<String, List<String>> attendeesBySession(LocalDate from, LocalDate to) {
        List<LessonAttendance> rows = lessonAttendanceRepository.findBySessionDateBetween(from, to);
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        userRepository.findAllById(rows.stream().map(LessonAttendance::getUserId).collect(Collectors.toSet()))
                .forEach(u -> names.put(u.getId(), u.getUsername()));

        Map<String, List<String>> out = new HashMap<>();
        for (LessonAttendance row : rows) {
            String name = names.get(row.getUserId());
            if (name != null) {
                out.computeIfAbsent(key(row.getLectureId(), row.getGroupId(), row.getSessionDate()),
                        k -> new ArrayList<>()).add(name);
            }
        }
        out.values().forEach(list -> list.sort(String::compareTo));
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssistantAttendanceResponse> sessionAttendance(UUID lectureId, UUID groupId, LocalDate sessionDate) {
        Set<UUID> present = lessonAttendanceRepository.findForSession(lectureId, groupId, sessionDate).stream()
                .map(LessonAttendance::getUserId)
                .collect(Collectors.toSet());
        return userRepository.findByRoleAndAdminIdOrderByUsername(Role.USER, TenantContext.get()).stream()
                .map(u -> new AssistantAttendanceResponse(u.getId(), u.getUsername(), present.contains(u.getId())))
                .toList();
    }

    @Override
    @Transactional
    public void setAttendance(AttendanceRequest request) {
        // Only this admin's assistants may be marked; anything else is ignored
        // rather than trusted from the request body.
        Set<UUID> assistants = userRepository
                .findByRoleAndAdminIdOrderByUsername(Role.USER, TenantContext.get()).stream()
                .map(User::getId)
                .collect(Collectors.toSet());

        // Replace the set: clear the session, flush so the unique index cannot trip
        // mid-transaction, then insert the chosen assistants.
        lessonAttendanceRepository.deleteForSession(request.lectureId(), request.groupId(), request.sessionDate());
        lessonAttendanceRepository.flush();
        for (UUID userId : request.userIdsOrEmpty().stream().filter(java.util.Objects::nonNull).distinct().toList()) {
            if (!assistants.contains(userId)) {
                continue;
            }
            LessonAttendance row = new LessonAttendance();
            row.setLectureId(request.lectureId());
            row.setGroupId(request.groupId());
            row.setSessionDate(request.sessionDate());
            row.setUserId(userId);
            lessonAttendanceRepository.save(row);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssistantAttendanceRecordResponse> assistantAttendanceLog(UUID userId) {
        List<LessonAttendance> rows = lessonAttendanceRepository.findByUserIdOrderBySessionDateDescCreatedAtDesc(userId);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, Lecture> lectures = byId(lectureRepository.findAllById(
                rows.stream().map(LessonAttendance::getLectureId).collect(Collectors.toSet())),
                Lecture::getId);
        Map<UUID, Group> groups = byId(groupRepository.findAllById(
                rows.stream().map(LessonAttendance::getGroupId).filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet())),
                Group::getId);

        List<AssistantAttendanceRecordResponse> log = new ArrayList<>(rows.size());
        for (LessonAttendance row : rows) {
            Lecture lecture = lectures.get(row.getLectureId());
            Group group = row.getGroupId() == null ? null : groups.get(row.getGroupId());
            log.add(new AssistantAttendanceRecordResponse(
                    row.getSessionDate(),
                    lecture == null ? "حصة محذوفة" : lecture.getName(),
                    groupLabel(group),
                    group == null ? null : group.getCenterName(),
                    group != null ? group.getGrade() : lecture == null ? null : lecture.getGrade()));
        }
        return log;
    }

    /** "الأحد · ٥ م" - the slot the group actually sits in, not a name it lacks. */
    private static String groupLabel(Group group) {
        if (group == null) {
            return "بدون مجموعة";
        }
        int day = group.getDayOfWeek();
        String name = day >= 0 && day < DAYS.length ? DAYS[day] : "";
        return (name + " · " + arabicTime(group.getStartTime())).strip();
    }

    /**
     * A wall-clock time in the 12-hour Arabic reading the whole system uses:
     * ٤ م, ٤:٣٠ م, ٩ ص. Minutes are dropped when zero, because "four in the
     * afternoon" is how the hour is actually said. Mirrors the web's
     * {@code fmtTime} - this copy exists because the PDF is rendered here.
     */
    private static String arabicTime(LocalTime time) {
        if (time == null) {
            return "";
        }
        int hour = time.getHour();
        int minute = time.getMinute();
        String period = hour < 12 ? "ص" : "م";
        int twelve = hour % 12 == 0 ? 12 : hour % 12;
        return minute == 0
                ? arabicDigits(String.valueOf(twelve)) + " " + period
                : arabicDigits(twelve + ":" + String.format("%02d", minute)) + " " + period;
    }

    private static String arabicDigits(String value) {
        StringBuilder b = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            b.append(c >= '0' && c <= '9' ? (char) ('٠' + (c - '0')) : c);
        }
        return b.toString();
    }

    private FinanceEntry apply(FinanceEntry entry, FinanceEntryRequest request) {
        entry.setLectureId(request.lectureId());
        entry.setGroupId(request.groupId());
        entry.setSessionDate(request.sessionDate());
        entry.setKind(request.kind());
        entry.setDescription(request.description().strip());
        entry.setAmount(request.amount());
        return entry;
    }

    private static FinanceEntryResponse toResponse(FinanceEntry entry) {
        return new FinanceEntryResponse(
                entry.getId(),
                entry.getLectureId(),
                entry.getGroupId(),
                entry.getSessionDate(),
                entry.getKind(),
                entry.getDescription(),
                up(entry.getAmount()),
                entry.getVersion());
    }

    /** Whole pounds, always rounded up - the teacher is never shown a part-pound. */
    private static BigDecimal up(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(0, RoundingMode.CEILING);
    }

    private static String key(UUID lectureId, UUID groupId, LocalDate date) {
        return lectureId + ":" + (groupId == null ? "none" : groupId) + ":" + date;
    }

    private static <T> Map<UUID, T> byId(List<T> rows, java.util.function.Function<T, UUID> id) {
        Map<UUID, T> map = new HashMap<>(rows.size());
        for (T row : rows) {
            map.put(id.apply(row), row);
        }
        return map;
    }
}
