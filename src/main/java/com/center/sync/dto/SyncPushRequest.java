package com.center.sync.dto;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import jakarta.validation.constraints.Size;

/**
 * A batch of offline writes to apply.
 *
 * <p>The size cap is load protection, not policy. Every mutation in a push runs
 * in its own {@code REQUIRES_NEW} transaction, so the batch size IS the number
 * of transactions one HTTP request costs - and the list was previously
 * unbounded, meaning a single request could hold a Tomcat thread and churn the
 * connection pool for as long as it took to apply tens of thousands of rows.
 * The shipped sync engine sends 50 at a time ({@code SyncEngine.pushBatch}), so
 * 500 is ten times the real traffic and still a hard ceiling on the damage a
 * hand-rolled or corrupted client queue can do.
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record SyncPushRequest(
        @Size(max = 500, message = "دفعة المزامنة كبيرة جدًا") List<SyncMutation> mutations) {
}
