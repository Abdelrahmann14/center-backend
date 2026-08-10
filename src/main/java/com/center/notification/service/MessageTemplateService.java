package com.center.notification.service;

import java.util.List;
import java.util.Map;

import com.center.notification.dto.MessageTemplateCreateRequest;
import com.center.notification.dto.MessageTemplateUpdateRequest;
import com.center.notification.dto.MessageTemplateResponse;

/** Editable bodies for the automatic system messages (OTP, resets, link, ...). */
public interface MessageTemplateService {

    List<MessageTemplateResponse> list();

    MessageTemplateResponse create(MessageTemplateCreateRequest request);

    MessageTemplateResponse update(String code, MessageTemplateUpdateRequest request);

    /** Removes a custom template (system templates cannot be deleted). */
    void delete(String code);

    MessageTemplateResponse setEnabled(String code, boolean enabled);

    /**
     * Loads a template by code and interpolates {placeholders}. Falls back to a
     * baked-in default when the row is missing or its body is blank, so a critical
     * message (e.g. a verification code) is always deliverable.
     */
    Rendered render(String code, Map<String, String> vars);

    record Rendered(String title, String body) {
    }
}
