package com.center.student.dto;

import java.util.List;
import java.util.UUID;

/**
 * Everyone else on the roster who collides with one student.
 *
 * <p>Read by the attendance desk, which is where a collision is actually
 * discovered: the students page can spot a duplicate because it holds the whole
 * roster in the browser and can count names, but the registration screen loads
 * one student at a time and had no way to know that the name it just resolved
 * belongs to two people. Somebody scanning a card learned nothing, and marked
 * the wrong student present.
 *
 * <p>The three kinds are kept apart because they mean different things and only
 * two of them are a fault:
 *
 * <ul>
 *   <li><b>{@code sameName}</b> - two records with identical names. Real people
 *       do share names, but at a desk it means the code on the card is the only
 *       thing telling them apart, and the reader should be told that.
 *   <li><b>{@code studentPhone}</b> - a number the student themselves carries is
 *       on somebody else's record. Usually a bad import or a mistyped digit; it
 *       also means a card sent to this number reaches the wrong person.
 *   <li><b>{@code parentPhone}</b> - a guardian number shared with another
 *       record. Overwhelmingly siblings, which is correct data, so this is a
 *       remark and never a warning.
 * </ul>
 *
 * <p>Advisory throughout. Nothing here blocks a registration - the desk is told
 * and decides.
 */
public record StudentConflictResponse(
        List<Peer> sameName,
        List<PhoneClash> studentPhone,
        List<PhoneClash> parentPhone) {

    /**
     * A colliding student, named well enough to be told apart from the one on
     * screen: the code is on their card and the grade is what the desk asks.
     *
     * @param asGuardian how the OTHER record holds the shared number - false when
     *                   it is their own, true when it is their guardian's. Null
     *                   for a name clash, where no number is involved
     */
    public record Peer(UUID id, Integer serial, String name, String grade, Boolean asGuardian) {
    }

    /** One number, and every other record carrying it. */
    public record PhoneClash(String phone, List<Peer> peers) {
    }
}
