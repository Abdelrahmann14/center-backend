package com.center.notification.service;

import java.util.List;

import com.center.notification.dto.AdminBroadcastRequest;
import com.center.notification.dto.BroadcastResult;
import com.center.notification.dto.MessageTemplateCreateRequest;
import com.center.notification.dto.MessageTemplateResponse;
import com.center.notification.dto.MessageTemplateUpdateRequest;
import com.center.notification.dto.MessagingRecipient;
import com.center.notification.service.VariableCatalog.Variable;

/**
 * An admin's own, tenant-scoped notifications + messages: broadcast to their own
 * students/parents, manage their own message templates, and view their history.
 */
public interface AdminMessagingService {

    BroadcastResult broadcast(AdminBroadcastRequest request);

    List<com.center.notification.dto.OutgoingMessageResponse> outgoing();

    void deleteOutgoing(java.util.UUID id);

    /** The admin's own custom templates plus the read-only system templates. */
    List<MessageTemplateResponse> templates();

    MessageTemplateResponse createTemplate(MessageTemplateCreateRequest request);

    MessageTemplateResponse updateTemplate(String code, MessageTemplateUpdateRequest request);

    void deleteTemplate(String code);

    MessageTemplateResponse setTemplateEnabled(String code, boolean enabled);

    List<Variable> variables();

    List<MessagingRecipient> searchStudents(String q);

    List<MessagingRecipient> searchParents(String q);
}
