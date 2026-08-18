package com.center.whatsapp.service;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.common.tenant.TenantContext;
import com.center.whatsapp.dto.WhatsappCheckResponse;
import com.center.whatsapp.entity.WhatsappNumber;
import com.center.whatsapp.repository.WhatsappNumberRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The workspace's memory of which numbers are on WhatsApp.
 *
 * <p>Green API is asked about a number once. After that the stored answer is
 * served, which is what lets a student form be filled with no connection: the
 * number is queued instead of checked, the form no longer waits on the network,
 * and {@link WhatsappNumberJob} resolves it when the line comes back.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsappNumberService {

    /** Give up on a number after this many failed lookups. */
    public static final int MAX_ATTEMPTS = 6;

    private final WhatsappNumberRepository repository;
    private final GreenApiClient greenApiClient;

    /**
     * Routes the transactional halves of {@link #drainPending} back through the
     * proxy; a plain {@code this.} call would bypass it and put the whole pass -
     * network included - back inside one transaction.
     */
    @Autowired
    @Lazy
    private WhatsappNumberService self;

    /** Digits only - the shape the clients send and the shape we store. */
    public static String normalise(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    /**
     * The stored answer, or a live lookup that is then remembered.
     *
     * <p>An unreachable Green API is reported as unchecked rather than as "no
     * WhatsApp": the caller must not turn an outage into a wrong red badge.
     */
    @Transactional
    public WhatsappCheckResponse lookup(String phone) {
        String number = normalise(phone);
        if (number == null) {
            return new WhatsappCheckResponse(true, false);
        }

        WhatsappNumber known = repository.findByPhone(number).orElse(null);
        if (known != null && known.answered()) {
            return new WhatsappCheckResponse(known.getHasWhatsapp(), true);
        }

        WhatsappNumber row = known != null ? known : new WhatsappNumber(number);
        boolean resolved = resolve(row);
        repository.save(row);
        return resolved
                ? new WhatsappCheckResponse(row.getHasWhatsapp(), true)
                : new WhatsappCheckResponse(true, false);
    }

    /**
     * Remember these numbers as needing an answer, without asking now. Called
     * for phones that arrived from an offline client, where the check could not
     * run at the time they were typed.
     */
    @Transactional
    public void queue(Collection<String> phones) {
        if (phones == null || phones.isEmpty()) {
            return;
        }
        List<String> wanted = phones.stream()
                .map(WhatsappNumberService::normalise)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (wanted.isEmpty()) {
            return;
        }
        // Idempotent per number: the database drops a duplicate rather than
        // throwing, so this can never abort the transaction that called it (the
        // offline replay queues these while saving the student). @TenantId is not
        // applied to a native insert, so the workspace is passed explicitly.
        UUID admin = TenantContext.get();
        for (String phone : wanted) {
            repository.queueIfAbsent(admin, phone);
        }
    }

    /**
     * Queue student phones in the CURRENT workspace that were never asked about.
     *
     * <p>Nothing else discovers numbers: {@link #lookup} only learns about a
     * number somebody typed into the student form, and {@code queue} is called
     * only from the offline replay path. A roster that already existed when this
     * feature shipped was therefore invisible to it forever - which is exactly
     * what made the "without WhatsApp" filter come back empty on a full roster.
     * Running this before every drain closes the gap and keeps it closed.
     *
     * @return how many numbers were newly queued
     */
    @Transactional
    public int enqueueUnknownStudentPhones(int limit) {
        List<String> phones = repository.findUnknownStudentPhones(TenantContext.get(), limit);
        queue(phones);
        return phones.size();
    }

    /**
     * Answer up to {@code limit} queued numbers in the CURRENT workspace.
     * Returns how many got a real answer.
     *
     * <p>Deliberately NOT {@code @Transactional}. It used to be, and with a
     * limit of 60 that meant one write transaction held open across sixty
     * sequential Green API round trips - up to twenty minutes of a pooled
     * connection, once every five minutes, per workspace, on a pool of eight.
     * The connection is now held only for the read that selects the batch and
     * the write that records it; the network sits between them holding nothing.
     *
     * <p>The trade is that a pass killed mid-flight records none of its
     * attempts rather than some. That is harmless: nothing was consumed, and the
     * next pass simply asks again.
     */
    public int drainPending(int limit) {
        List<WhatsappNumber> pending = self.duePending(limit);
        if (pending.isEmpty()) {
            return 0;
        }
        int resolved = 0;
        for (WhatsappNumber row : pending) {
            if (resolve(row)) {
                resolved++;
            }
        }
        self.persist(pending);
        return resolved;
    }

    /** The batch this pass will ask about. Detached on purpose - see above. */
    @Transactional(readOnly = true)
    public List<WhatsappNumber> duePending(int limit) {
        return repository.findByHasWhatsappIsNullAndAttemptsLessThanOrderByCreatedAtAsc(
                MAX_ATTEMPTS, Limit.of(limit));
    }

    /** Record a finished pass. No network call may happen inside this. */
    @Transactional
    public void persist(List<WhatsappNumber> rows) {
        repository.saveAll(rows);
    }

    /** Which of these numbers are known NOT to be on WhatsApp. */
    @Transactional(readOnly = true)
    public Set<String> withoutWhatsapp(Collection<String> phones) {
        List<String> wanted = phones.stream()
                .map(WhatsappNumberService::normalise)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (wanted.isEmpty()) {
            return Set.of();
        }
        return repository.findByPhoneIn(wanted).stream()
                .filter(n -> Boolean.FALSE.equals(n.getHasWhatsapp()))
                .map(WhatsappNumber::getPhone)
                .collect(Collectors.toSet());
    }

    /**
     * One Green API attempt. Writes {@code hasWhatsapp} only on a real answer, so
     * a network failure never gets recorded as "not on WhatsApp".
     *
     * @return true if the number now has an answer
     */
    private boolean resolve(WhatsappNumber row) {
        row.setAttempts(row.getAttempts() + 1);
        try {
            GreenApiClient.WhatsappCheck check = greenApiClient.checkWhatsapp(row.getPhone());
            if (!check.checked()) {
                row.setLastError("تعذّر الوصول إلى واتساب");
                return false;
            }
            row.setHasWhatsapp(check.existsWhatsapp());
            row.setCheckedAt(OffsetDateTime.now());
            row.setLastError(null);
            return true;
        } catch (RuntimeException ex) {
            log.warn("whatsapp check failed for {}: {}", row.getPhone(), ex.getMessage());
            row.setLastError(Optional.ofNullable(ex.getMessage()).orElse("خطأ غير معروف"));
            return false;
        }
    }
}
