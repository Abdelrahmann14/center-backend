package com.center.notification.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.notification.dto.MessageTemplateCreateRequest;
import com.center.notification.dto.MessageTemplateUpdateRequest;
import com.center.notification.dto.MessageTemplateResponse;
import com.center.notification.entity.MessageTemplate;
import com.center.common.exception.BusinessRuleException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.notification.repository.MessageTemplateRepository;
import com.center.notification.service.MessageTemplateService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MessageTemplateServiceImpl implements MessageTemplateService {

    private final MessageTemplateRepository repository;

    /**
     * Baked-in defaults, identical to the V35 seed, used only if a row is ever
     * missing or blank - the automatic messages must never fail to render.
     * [title, body]; title null for WhatsApp-only messages.
     */
    private static final Map<String, String[]> FALLBACK = Map.ofEntries(
            Map.entry("student_verification", new String[] {null,
                    "يحاول شخص ما إنشاء حساب باسمك في تطبيق الطالب.\n"
                            + "رمز التحقق: {code}\n"
                            + "صالح لمدة {minutes} دقائق. إذا لم تكن أنت، تجاهل هذه الرسالة."}),
            Map.entry("student_password_reset", new String[] {null,
                    "طلب إعادة تعيين كلمة المرور لحسابك في تطبيق الطالب.\n"
                            + "رمز التحقق: {code}\n"
                            + "صالح لمدة {minutes} دقائق. إذا لم تكن أنت، تجاهل هذه الرسالة."}),
            Map.entry("parent_password_reset", new String[] {null,
                    "طلب إعادة تعيين كلمة المرور لحسابك بصفتك ولي أمر.\n"
                            + "رمز التحقق: {code}\n"
                            + "صالح لمدة {minutes} دقائق. إذا لم تكن أنت، تجاهل هذه الرسالة."}),
            Map.entry("parent_link_request", new String[] {"طلب ربط ولي أمر",
                    "قام ({name}) بطلب ربط حسابه بحسابك بصفته ولي أمر. "
                            + "افتح الإعدادات ثم أولياء الأمور للموافقة على الطلب أو رفضه."}),
            Map.entry("parent_link_approved_wa", new String[] {null,
                    "تم التحقق من أنك ولي أمر الطالب ({name}) وتفعيل حسابك بنجاح.\n"
                            + "يمكنك الآن تسجيل الدخول إلى التطبيق."}),
            Map.entry("parent_link_approved", new String[] {"تم قبول الطلب",
                    "تمت الموافقة على ربط حسابك بالطالب ({name}) بنجاح."}),
            Map.entry("parent_link_rejected_wa", new String[] {null,
                    "تعذّر التحقق من صلتك بالطالب. يرجى التأكد من إدخال كود الطالب الصحيح."}),
            Map.entry("parent_link_rejected", new String[] {"تم رفض الطلب",
                    "تعذّر ربط حسابك بالطالب ({name})."}),
            Map.entry("exam_result", new String[] {null,
                    "حصل {student.name} على {exam.score} من {exam.max} "
                            + "في اختبار \"{exam.name}\"."}));

    @Override
    @Transactional(readOnly = true)
    public List<MessageTemplateResponse> list() {
        return repository.findAllByOrderByChannelDescCode().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public MessageTemplateResponse create(MessageTemplateCreateRequest request) {
        boolean notification = "notification".equals(request.channel());
        MessageTemplate template = new MessageTemplate();
        template.setCode("custom_" + UUID.randomUUID().toString().substring(0, 8));
        template.setName(request.name().strip());
        template.setChannel(request.channel());
        template.setTitle(notification && request.title() != null && !request.title().isBlank()
                ? request.title().strip() : null);
        template.setBody(request.body().strip());
        template.setEnabled(true);
        template.setSystem(false);
        template.setUpdatedAt(OffsetDateTime.now());
        repository.save(template);
        return toResponse(template);
    }

    @Override
    @Transactional
    public void delete(String code) {
        MessageTemplate template = repository.findById(code)
                .orElseThrow(() -> new ResourceNotFoundException("القالب غير موجود"));
        if (template.isSystem()) {
            throw new BusinessRuleException("لا يمكن حذف رسائل النظام");
        }
        repository.delete(template);
    }

    @Override
    @Transactional
    public MessageTemplateResponse setEnabled(String code, boolean enabled) {
        MessageTemplate template = repository.findById(code)
                .orElseThrow(() -> new ResourceNotFoundException("القالب غير موجود"));
        template.setEnabled(enabled);
        template.setUpdatedAt(OffsetDateTime.now());
        repository.save(template);
        return toResponse(template);
    }

    @Override
    @Transactional
    public MessageTemplateResponse update(String code, MessageTemplateUpdateRequest request) {
        MessageTemplate template = repository.findById(code)
                .orElseThrow(() -> new ResourceNotFoundException("القالب غير موجود"));
        // A WhatsApp-only template keeps its null title even if one is submitted.
        if (template.getTitle() != null) {
            template.setTitle(request.title() == null ? null : request.title().strip());
        }
        template.setBody(request.body().strip());
        template.setUpdatedAt(OffsetDateTime.now());
        repository.save(template);
        return toResponse(template);
    }

    @Override
    @Transactional(readOnly = true)
    public Rendered render(String code, Map<String, String> vars) {
        MessageTemplate template = repository.findById(code).orElse(null);
        String title;
        String body;
        if (template != null && template.getBody() != null && !template.getBody().isBlank()) {
            title = template.getTitle();
            body = template.getBody();
        } else {
            String[] def = FALLBACK.get(code);
            if (def == null) {
                throw new ResourceNotFoundException("القالب غير موجود: " + code);
            }
            title = def[0];
            body = def[1];
        }
        return new Rendered(interpolate(title, vars), interpolate(body, vars));
    }

    private static String interpolate(String template, Map<String, String> vars) {
        if (template == null) {
            return null;
        }
        String out = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    private MessageTemplateResponse toResponse(MessageTemplate t) {
        return new MessageTemplateResponse(t.getCode(), t.getName(), t.getChannel(),
                t.getTitle(), t.getBody(), t.getVariables(), t.isEnabled(), t.isSystem(),
                t.getCreatedAt(), t.getCreatedBy(), t.getUpdatedAt(), t.getUpdatedBy());
    }
}
