package com.center.whatsapp.cloud.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Adopt one template by the id printed on its page in WhatsApp Manager.
 *
 * <p>By id rather than by name because a name is not unique - the same template
 * name exists once per language - and because a typo in a name would be stored
 * happily and only fail at the moment a parent was due a message.
 *
 * @param metaTemplateId the numeric id Meta shows on the template
 * @param label          an optional plain-Arabic name for the screens
 */
public record CloudTemplateImportRequest(
        @NotBlank(message = "مطلوب") @Size(max = 40) String metaTemplateId,
        @Size(max = 120) String label) {
}
