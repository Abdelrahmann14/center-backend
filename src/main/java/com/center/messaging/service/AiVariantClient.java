package com.center.messaging.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.center.common.config.ApplicationProperties;
import com.center.common.config.OutboundHttpConfig;
import com.center.common.exception.BusinessRuleException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Generates alternative wordings of a message through an OpenAI-compatible chat
 * API (Groq by default). It asks for reworded versions that keep the exact same
 * meaning and preserve every {placeholder}, and returns them as plain strings.
 */
@Service
@Slf4j
public class AiVariantClient {

    private static final String PATH = "/chat/completions";

    private final ApplicationProperties props;
    private final ObjectMapper mapper;
    private final RestClient rest;

    public AiVariantClient(ApplicationProperties props, ObjectMapper mapper,
            @Qualifier(OutboundHttpConfig.AI) RestClient rest) {
        this.props = props;
        this.mapper = mapper;
        this.rest = rest;
    }

    /** Whether generation can be attempted (an API key is configured). */
    public boolean configured() {
        return props.ai().configured();
    }

    /**
     * Reword {@code base} into {@code count} distinct alternatives with the same
     * meaning. Throws a business error the UI can show if the service is not
     * configured or the call fails.
     */
    public List<String> generate(String base, int count) {
        ApplicationProperties.Ai ai = props.ai();
        if (!ai.configured()) {
            throw new BusinessRuleException(
                    "لم يتم إعداد خدمة الذكاء الاصطناعي بعد. أضف مفتاح GROQ_API_KEY في إعدادات الخادم.");
        }
        if (base == null || base.isBlank()) {
            throw new BusinessRuleException("اكتب الرسالة الأساسية أولاً قبل توليد الصيغ البديلة");
        }

        String system = """
                أنت مساعد يعيد صياغة رسائل واتساب قصيرة بالعربية لسنتر تعليمي.
                أعد صياغة الرسالة بـ %d صيغ مختلفة في الأسلوب وترتيب الجُمل، مع الحفاظ على
                نفس المعنى والغرض تمامًا. حافظ على أي متغيّر بين قوسين معقوفين مثل {student.name}
                كما هو حرفيًا دون تغيير أو ترجمة أو حذف. لا تضف أي شرح.
                أعد الناتج بصيغة JSON فقط على الشكل: {"variants": ["...", "..."]}.
                """.formatted(count);

        Map<String, Object> body = Map.of(
                "model", ai.model(),
                "temperature", 0.9,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", base)));

        String content;
        try {
            JsonNode res = rest.post()
                    .uri(ai.baseUrl() + PATH)
                    .header("Authorization", "Bearer " + ai.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            content = res == null ? null
                    : res.path("choices").path(0).path("message").path("content").asText(null);
        } catch (RestClientException ex) {
            log.error("AI variant generation failed: {}", ex.getMessage());
            throw new BusinessRuleException("تعذّر توليد الصيغ البديلة، حاول مرة أخرى");
        }

        List<String> variants = parse(content);
        if (variants.isEmpty()) {
            throw new BusinessRuleException("تعذّر توليد الصيغ البديلة، حاول مرة أخرى");
        }
        return variants.size() > count ? variants.subList(0, count) : variants;
    }

    /** Pulls the strings out of {"variants":[...]}, or a bare [...] as a fallback. */
    private List<String> parse(String content) {
        List<String> out = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return out;
        }
        try {
            JsonNode root = mapper.readTree(content);
            JsonNode array = root.isArray() ? root : root.path("variants");
            if (array.isArray()) {
                for (JsonNode node : array) {
                    String text = node.isTextual() ? node.asText() : node.path("body").asText(null);
                    if (text != null && !text.isBlank()) {
                        out.add(text.strip());
                    }
                }
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("AI returned non-JSON content: {}", ex.getMessage());
        }
        return out;
    }
}
