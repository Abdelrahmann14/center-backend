package com.center.whatsapp.check;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.center.common.config.ApplicationProperties;
import com.center.whatsapp.service.WaPhone;

import lombok.RequiredArgsConstructor;

/**
 * "Is this number on WhatsApp", answered from a shared cache and topped up from
 * Green API when the cache has nothing fresh.
 *
 * <p>The cache is the point. A check is a call to a third party and Green rate
 * limits them, so asking once per number and remembering the answer is what
 * makes this usable on a roster of hundreds - and because the table is not
 * tenant-scoped, a guardian shared between two teachers is one check, not two.
 */
@Service
@RequiredArgsConstructor
public class WhatsappNumberCheckService {

    /**
     * Numbers checked in one request.
     *
     * <p>Each is a round trip to Green, so a whole roster in one request would
     * outlast every timeout in the way and hand back nothing. The caller loops
     * and watches the count fall - the same shape as the barcode send, and for
     * the same reason.
     */
    public static final int BATCH = 25;

    /**
     * This service through its own proxy: {@link #save} must start its own
     * transaction, and a plain {@code this.save(...)} would never reach the
     * proxy that gives it one.
     */
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private WhatsappNumberCheckService self;

    private final GreenNumberCheckClient client;
    private final WhatsappNumberCheckRepository repo;
    private final ApplicationProperties props;

    public boolean configured() {
        return client.configured();
    }

    private OffsetDateTime trustedSince() {
        return OffsetDateTime.now().minus(props.numberCheck().ttl());
    }

    /** Everything currently known, keyed by the roster's local phone form. */
    @Transactional(readOnly = true)
    public Map<String, Boolean> known() {
        Map<String, Boolean> out = new HashMap<>();
        for (WhatsappNumberCheck row : repo.allFresh(trustedSince())) {
            out.put(row.getPhone(), row.isExistsWhatsapp());
        }
        return out;
    }

    /** Which of these numbers has no fresh answer, in a stable order. */
    @Transactional(readOnly = true)
    public List<String> unanswered(Collection<String> phones) {
        Set<String> wanted = new LinkedHashSet<>();
        for (String p : phones) {
            String key = WaPhone.local(p);
            if (!key.isEmpty()) {
                wanted.add(key);
            }
        }
        if (wanted.isEmpty()) {
            return List.of();
        }
        for (WhatsappNumberCheck row : repo.freshFor(wanted, trustedSince())) {
            wanted.remove(row.getPhone());
        }
        return new ArrayList<>(wanted);
    }

    /** What one batch of checking did, and how many are still unanswered. */
    public record CheckResult(int checked, int failed, int remaining, String blockedReason) {
    }

    /**
     * Ask Green about the next {@link #BATCH} numbers that have no fresh answer.
     *
     * <p>A number Green could not answer for is left uncached on purpose, so it
     * is asked again next time rather than being recorded as having no WhatsApp.
     * A timeout is not a fact about the family.
     */
    public CheckResult checkNext(Collection<String> phones) {
        if (!client.configured()) {
            return new CheckResult(0, 0, unanswered(phones).size(),
                    "خدمة فحص الأرقام غير مُفعّلة — أضِف بيانات الفحص من إعدادات المنصة");
        }
        List<String> todo = unanswered(phones);
        int checked = 0;
        int failed = 0;
        for (String phone : todo.stream().limit(BATCH).toList()) {
            Optional<Boolean> answer = client.existsWhatsapp(phone);
            if (answer.isEmpty()) {
                failed++;
                continue;
            }
            self.save(phone, answer.get());
            checked++;
        }
        return new CheckResult(checked, failed, Math.max(0, todo.size() - checked), null);
    }

    /**
     * Its own transaction per number: the Green calls happen outside any, and one
     * unanswerable number must not roll back the answers already earned.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(String phone, boolean exists) {
        WhatsappNumberCheck row = repo.findById(phone).orElseGet(() -> {
            WhatsappNumberCheck fresh = new WhatsappNumberCheck();
            fresh.setPhone(phone);
            return fresh;
        });
        row.setExistsWhatsapp(exists);
        row.setCheckedAt(OffsetDateTime.now());
        repo.save(row);
    }
}
