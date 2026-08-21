package com.center.whatsapp.check;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.center.common.config.ApplicationProperties;
import com.center.whatsapp.service.WaPhone;

import lombok.extern.slf4j.Slf4j;

/**
 * Asks Green API whether one phone number is on WhatsApp.
 *
 * <p>This is the entire surface of Green in the platform. It cannot send: there
 * is one method, it takes a number and returns a yes/no, and no message, card,
 * template or media passes through it. Every message goes through Meta.
 *
 * <p>It exists because the official API has no equivalent and will not get one.
 * Cloud API exposes sending, media, templates and phone-number administration;
 * the on-premises {@code POST /v1/contacts} that answered this question was
 * sunset with the on-premises API in October 2025. Green runs a real WhatsApp
 * client, so it can ask what a business API is not allowed to.
 *
 * <p>One instance serves the whole platform - see
 * {@link ApplicationProperties.NumberCheck} for why it is not per teacher.
 */
@Component
@Slf4j
public class GreenNumberCheckClient {

    private final ApplicationProperties props;
    private final RestClient http;

    public GreenNumberCheckClient(ApplicationProperties props, RestClient.Builder builder) {
        this.props = props;
        this.http = builder.build();
    }

    public boolean configured() {
        return props.numberCheck().configured();
    }

    /**
     * Whether {@code phone} is on WhatsApp.
     *
     * <p>{@link Optional#empty()} means "could not find out" - not configured,
     * the instance is down, the rate limit was hit - and is deliberately distinct
     * from {@code false}. Nothing may record "this family has no WhatsApp"
     * because a third party timed out; an unknown answer has to stay unknown, or
     * the cache fills with confident wrong entries that nobody re-checks.
     */
    public Optional<Boolean> existsWhatsapp(String phone) {
        ApplicationProperties.NumberCheck cfg = props.numberCheck();
        if (!cfg.configured() || phone == null || phone.isBlank()) {
            return Optional.empty();
        }
        String url = cfg.baseUrl() + "/waInstance" + cfg.instanceId()
                + "/checkWhatsapp/" + cfg.token();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = http.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    // Green wants international digits with no plus.
                    .body(Map.of("phoneNumber", Long.parseLong(WaPhone.international(phone))))
                    .retrieve()
                    .body(Map.class);
            Object exists = body == null ? null : body.get("existsWhatsapp");
            return exists instanceof Boolean b ? Optional.of(b) : Optional.empty();
        } catch (NumberFormatException ex) {
            // Not a number Green could be asked about in the first place.
            return Optional.empty();
        } catch (RestClientException ex) {
            log.warn("WhatsApp number check failed for {}: {}", phone, ex.getMessage());
            return Optional.empty();
        }
    }
}
