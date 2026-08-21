package com.center.whatsapp.cloud.dto;

/**
 * One message type as the parent will actually receive it.
 *
 * <p>Built for a teacher who wants to read what goes out in their name. The text
 * is the approved template's own wording with the placeholders already filled
 * from the variable catalogue's examples, because {@code {{1}}} tells a teacher
 * nothing and "أحمد محمد" tells them everything.
 *
 * @param text          the filled body, or null when no approved template carries
 *                      this type - the screen then explains rather than showing
 *                      an empty bubble
 * @param carriesFile   the message ships a PDF, drawn as an attachment row
 * @param blockedReason why this type cannot be sent right now, in Arabic
 */
public record WhatsappMessagePreview(
        String code,
        String label,
        String description,
        boolean carriesFile,
        boolean ready,
        String blockedReason,
        String templateName,
        String text) {
}
