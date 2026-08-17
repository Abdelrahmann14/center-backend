package com.center.finance.dto;

import java.math.BigDecimal;

/**
 * One payment bucket on an invoice: everybody paying the same amount.
 *
 * @param price      what each student in this bucket pays; null = no price set
 * @param count      how many of them
 * @param subtotal   price times count, rounded up
 * @param discounted true when this bucket pays below the center's official price
 */
public record InvoiceLineResponse(
        BigDecimal price,
        long count,
        BigDecimal subtotal,
        boolean discounted) {
}
