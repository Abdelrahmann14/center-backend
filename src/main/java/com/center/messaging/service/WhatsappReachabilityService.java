package com.center.messaging.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.common.exception.BusinessRuleException;
import com.center.common.tenant.TenantContext;
import com.center.messaging.repository.WhatsappMessageLogRepository;
import com.center.whatsapp.service.WaPhone;

import lombok.RequiredArgsConstructor;

/**
 * Which of the workspace's numbers are actually reachable on WhatsApp.
 *
 * <p><b>Why this is derived and not asked.</b> The platform used to answer this
 * with a third-party client that could interrogate WhatsApp directly. The
 * official API cannot: there is no endpoint that takes a number and says whether
 * it is registered. The on-premises API had one ({@code POST /v1/contacts}) and
 * it went with the on-premises API itself; Cloud API exposes sending, media,
 * templates and phone-number administration, and nothing that inspects a
 * stranger's number. Meta's position is deliberate - it would be a free
 * enumeration oracle over every phone number in the world.
 *
 * <p>So the answer is learned instead of asked, from messages the workspace has
 * already sent. A delivery report is proof a WhatsApp client received it. Error
 * 131026 is Meta saying it could not put the message in front of anyone.
 *
 * <p><b>What that costs in certainty.</b> 131026 is a bucket error - Meta does
 * not disclose which of several causes applied, partly for the recipient's
 * privacy. Not being on WhatsApp is the commonest, but an unaccepted terms
 * update, a very old WhatsApp build, or Meta declining to deliver a marketing
 * message land on the same code. So this reports "we could not reach them",
 * which is true, rather than "they are not on WhatsApp", which would not be.
 *
 * <p>Everything here is scoped by workspace, so each teacher's answer comes from
 * their own number's traffic - what one teacher's number could not reach says
 * nothing about another's.
 */
@Service
@RequiredArgsConstructor
public class WhatsappReachabilityService {

    private final WhatsappMessageLogRepository logRepository;
    private final com.center.whatsapp.check.WhatsappNumberCheckService numberCheck;

    /**
     * Every number this workspace has learned something about.
     *
     * <p>Keyed by the local form the roster stores, so a page holding a student's
     * phone can look it up directly. A number that is absent is not "fine" - it
     * is unknown, which is the honest answer for someone who has never been
     * messaged, and the caller is expected to say so rather than assume.
     */
    @Transactional(readOnly = true)
    public Map<String, Boolean> reachability() {
        UUID admin = TenantContext.get();
        if (admin == null) {
            throw new BusinessRuleException("هذه الصفحة متاحة لحسابات المدرّسين فقط");
        }
        // Two sources, ranked by how much they actually know.
        //
        // Weakest first: the number check, which asks WhatsApp directly whether
        // the number is registered. It is a real answer and it covers numbers
        // nobody has ever messaged, which delivery reports never can.
        Map<String, Boolean> out = new HashMap<>(numberCheck.known());

        // Delivery reports on top, because they outrank it in both directions
        // for the question that matters here - not "does this number exist on
        // WhatsApp" but "do OUR messages get there". A delivery is proof they
        // did. A 131026 is Meta saying they did not, whatever the register says.
        for (WhatsappMessageLogRepository.PhoneReach row : logRepository.reachability(admin)) {
            String key = WaPhone.local(row.getPhone());
            if (key.isEmpty()) {
                continue;
            }
            // Two stored spellings of one number ("+2010..." and "010...") fold
            // to the same key. Good news wins the collision: one delivery proves
            // the person is there, whichever way the number was written down.
            out.merge(key, row.getReached(), (a, b) -> a || b);
        }
        return out;
    }

    /** How many roster numbers still have no answer from the check service. */
    @Transactional(readOnly = true)
    public int uncheckedCount(java.util.Collection<String> rosterPhones) {
        return numberCheck.unanswered(rosterPhones).size();
    }
}
