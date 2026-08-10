package com.center.analytics.dto;

import java.math.BigDecimal;

/**
 * A lesson's present students grouped by what they paid.
 *
 * @param price       null bucket = students with no price set
 * @param otherGroup  attended under a group other than their own
 * @param newStudents this lesson was their first
 */
public record PriceBucketResponse(
        BigDecimal price,
        long count,
        long male,
        long female,
        long otherGroup,
        long newStudents) {
}
