package com.center.messaging.dto;

import java.util.List;
import java.util.UUID;

/**
 * Who has already been messaged about one lesson, per kind of message. The
 * lesson-group roster marks each student "sent / not yet" from this, and its
 * two send buttons arm themselves over whoever is NOT in these lists.
 *
 * <p>Absence is not here: an absent student has no row on that roster, so its
 * "sent" flag travels with the absentee list itself.
 */
public record LectureMessageStatus(List<UUID> attendance, List<UUID> examGrade) {
}
