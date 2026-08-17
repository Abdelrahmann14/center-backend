package com.center.center.dto;

import java.math.BigDecimal;

/** @param percentage the center's share of this grade's takings, 0-100. */
public record CenterGradePriceResponse(String grade, BigDecimal price, BigDecimal percentage) {
}
