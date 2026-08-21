package com.center.whatsapp.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.common.config.ApplicationProperties;
import com.center.messaging.repository.WhatsappMessageLogRepository;
import com.center.messaging.repository.WhatsappMessageLogRepository.CategoryCount;
import com.center.messaging.repository.WhatsappMessageLogRepository.DailyVolume;
import com.center.messaging.repository.WhatsappMessageLogRepository.NumberUsage;
import com.center.messaging.repository.WhatsappMessageLogRepository.OriginCount;
import com.center.messaging.repository.WhatsappMessageLogRepository.UsageTotals;
import com.center.whatsapp.dto.WhatsappUsageResponse;
import com.center.whatsapp.entity.WhatsappInstance;

import lombok.RequiredArgsConstructor;

/**
 * The month's WhatsApp usage for one workspace, assembled from the message log.
 *
 * <p>The log is the only honest source: it has a row for every attempt, whether
 * it was delivered or refused. Counting anything anywhere else - the automations,
 * the roster, the numbers themselves - would count intent rather than messages.
 *
 * <p>Cost is an <b>estimate</b> and is named one everywhere it appears. WhatsApp
 * bills per delivered template message by category, at a rate that varies by
 * destination country and changes several times a year, so the figure here is
 * {@code delivered × the configured rate} and the invoice is the authority.
 */
@Service
@RequiredArgsConstructor
public class WhatsappUsageService {

    private static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");

    private final WhatsappMessageLogRepository logs;
    private final WhatsappInstanceService instances;
    private final ApplicationProperties properties;

    /**
     * @param owner the workspace to report on, which must also be the tenant the
     *              request is bound to - the log is tenant-scoped and the native
     *              queries below take the id explicitly
     * @param month {@code YYYY-MM}, or null for the current month in Cairo
     */
    @Transactional(readOnly = true)
    public WhatsappUsageResponse usage(UUID owner, String month) {
        YearMonth ym = parse(month);
        OffsetDateTime from = ym.atDay(1).atStartOfDay(CAIRO).toOffsetDateTime();
        OffsetDateTime to = ym.plusMonths(1).atDay(1).atStartOfDay(CAIRO).toOffsetDateTime();

        UsageTotals totals = logs.totals(owner, from, to);
        long attempted = totals == null ? 0 : totals.getAttempted();
        long sent = totals == null ? 0 : totals.getSent();

        List<NumberUsage> perNumber = logs.perNumber(owner, from, to);

        List<WhatsappUsageResponse.CategoryCost> costs = costs(logs.perCategory(owner, from, to));
        BigDecimal total = costs.stream()
                .map(WhatsappUsageResponse.CategoryCost::cost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new WhatsappUsageResponse(
                ym.toString(),
                attempted,
                sent,
                attempted - sent,
                totals == null ? 0 : totals.getRecipients(),
                logs.newContacts(owner, from, to),
                total,
                costs,
                numbers(owner, perNumber),
                daily(ym, logs.perDay(owner, from, to)),
                byType(logs.perOrigin(owner, from, to)));
    }

    /** {@code YYYY-MM}, falling back to the current Cairo month for anything else. */
    private static YearMonth parse(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(CAIRO);
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (RuntimeException ex) {
            return YearMonth.now(CAIRO);
        }
    }

    /**
     * Each number the workspace has, carrying whatever the log recorded against
     * it. Numbers with no traffic are still listed - "هذا الرقم لم يرسل شيئاً"
     * is an answer a teacher needs, and dropping the row would look like the
     * number had vanished.
     */
    private List<WhatsappUsageResponse.NumberUsage> numbers(UUID owner, List<NumberUsage> rows) {
        Map<UUID, long[]> byId = new HashMap<>();
        long[] unattributed = new long[2];
        for (NumberUsage r : rows) {
            long[] cell = r.getInstanceId() == null
                    ? unattributed
                    : byId.computeIfAbsent(r.getInstanceId(), k -> new long[2]);
            cell[0] += r.getAttempted();
            cell[1] += r.getSent();
        }

        List<WhatsappUsageResponse.NumberUsage> out = new ArrayList<>();
        for (WhatsappInstance w : instances.numbers(owner)) {
            long[] cell = byId.remove(w.getId());
            out.add(new WhatsappUsageResponse.NumberUsage(
                    w.getId(),
                    label(w),
                    w.getPhone(),
                    "authorized".equalsIgnoreCase(w.getState()),
                    w.getQualityRating(),
                    cell == null ? 0 : cell[0],
                    cell == null ? 0 : cell[1]));
        }

        // Whatever is left came from a number that has since been removed, or
        // from a send that failed before one was chosen. Both are real traffic
        // and both would otherwise vanish from a total the teacher can see.
        for (long[] cell : byId.values()) {
            unattributed[0] += cell[0];
            unattributed[1] += cell[1];
        }
        if (unattributed[0] > 0) {
            out.add(new WhatsappUsageResponse.NumberUsage(null, "رسائل بدون رقم محدد", null,
                    false, null, unattributed[0], unattributed[1]));
        }
        return out;
    }

    /** Every day of the month, so a chart has no holes to interpolate over. */
    private static List<WhatsappUsageResponse.DayVolume> daily(YearMonth ym, List<DailyVolume> rows) {
        Map<LocalDate, DailyVolume> byDay = new HashMap<>();
        for (DailyVolume r : rows) {
            byDay.put(r.getDay(), r);
        }
        List<WhatsappUsageResponse.DayVolume> out = new ArrayList<>(ym.lengthOfMonth());
        LocalDate today = LocalDate.now(CAIRO);
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            LocalDate day = ym.atDay(d);
            // A month still running stops at today rather than trailing a week of
            // zeros that read as "we stopped sending".
            if (day.isAfter(today)) {
                break;
            }
            DailyVolume r = byDay.get(day);
            out.add(new WhatsappUsageResponse.DayVolume(day,
                    r == null ? 0 : r.getAttempted(), r == null ? 0 : r.getSent()));
        }
        return out;
    }

    private List<WhatsappUsageResponse.CategoryCost> costs(List<CategoryCount> rows) {
        ApplicationProperties.Rates rates = properties.meta().rates();
        List<WhatsappUsageResponse.CategoryCost> out = new ArrayList<>();
        for (CategoryCount r : rows) {
            BigDecimal rate = rates.forCategory(r.getCategory());
            out.add(new WhatsappUsageResponse.CategoryCost(
                    r.getCategory(),
                    r.getSent(),
                    rate,
                    rate.multiply(BigDecimal.valueOf(r.getSent())).setScale(4, RoundingMode.HALF_UP)));
        }
        return out;
    }

    /** Log origins folded into the message types the teacher's screens name. */
    private static List<WhatsappUsageResponse.TypeVolume> byType(List<OriginCount> rows) {
        Map<String, long[]> totals = new HashMap<>();
        for (OriginCount r : rows) {
            long[] cell = totals.computeIfAbsent(
                    WhatsappResponsibilityCatalog.forOrigin(r.getOrigin()), k -> new long[2]);
            cell[0] += r.getAttempted();
            cell[1] += r.getSent();
        }
        List<WhatsappUsageResponse.TypeVolume> out = new ArrayList<>();
        for (WhatsappResponsibilityCatalog.Responsibility type : WhatsappResponsibilityCatalog.ALL) {
            long[] cell = totals.get(type.code());
            if (cell == null) {
                continue;
            }
            out.add(new WhatsappUsageResponse.TypeVolume(type.code(), type.label(),
                    cell[0], cell[1]));
        }
        return out;
    }

    private static String label(WhatsappInstance w) {
        if (w.getLabel() != null && !w.getLabel().isBlank()) {
            return w.getLabel();
        }
        if (w.getDisplayName() != null && !w.getDisplayName().isBlank()) {
            return w.getDisplayName();
        }
        return w.getPhone() != null && !w.getPhone().isBlank() ? "+" + w.getPhone() : "رقم واتساب";
    }
}
