package com.center.whatsapp.dto;

import java.util.UUID;

/** Assign a responsibility to a number, or unassign it when {@code instanceId} is null. */
public record WhatsappResponsibilityAssignRequest(UUID instanceId) {
}
