package com.center.whatsapp.dto;

import jakarta.validation.constraints.Size;

/** Rename a WhatsApp number (its friendly label). A blank label clears it. */
public record WhatsappLabelRequest(@Size(max = 60) String label) {
}
