package com.center.push.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import com.center.push.entity.PushToken;
import com.center.push.repository.PushTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class PushServiceImpl implements PushService {

    /** Expo's public push endpoint - it fans out to FCM/APNs; no server key needed here. */
    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";
    /** Expo accepts at most 100 messages per request. */
    private static final int BATCH = 100;

    private final PushTokenRepository pushTokenRepository;
    private final RestClient restClient = RestClient.create();

    @Override
    @Transactional
    public void register(UUID userId, String token, String platform) {
        if (token == null || token.isBlank()) {
            return;
        }
        // Atomic upsert: no read-then-write race, so two fast sign-ins on the same
        // device can never collide on the unique token.
        pushTokenRepository.upsert(userId, token, platform);
    }

    @Override
    @Transactional
    public void unregister(String token) {
        if (token != null && !token.isBlank()) {
            pushTokenRepository.deleteByToken(token);
        }
    }

    @Override
    @Async
    @Transactional(readOnly = true)
    public void sendToUsers(Collection<UUID> userIds, String title, String body, Map<String, Object> data) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<String> tokens = pushTokenRepository.findByUserIdIn(userIds).stream()
                .map(PushToken::getToken)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .toList();
        if (tokens.isEmpty()) {
            return;
        }

        for (int i = 0; i < tokens.size(); i += BATCH) {
            List<Map<String, Object>> batch = new ArrayList<>();
            for (String token : tokens.subList(i, Math.min(i + BATCH, tokens.size()))) {
                Map<String, Object> message = new LinkedHashMap<>();
                message.put("to", token);
                message.put("title", title);
                message.put("body", body);
                message.put("sound", "default");
                message.put("channelId", "default");
                // High priority so Android delivers/heads-up even in the background.
                message.put("priority", "high");
                if (data != null && !data.isEmpty()) {
                    message.put("data", data);
                }
                batch.add(message);
            }
            try {
                restClient.post()
                        .uri(EXPO_PUSH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(batch)
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                // Best-effort: a push failure must never break the triggering action.
                log.warn("Expo push send failed for {} token(s): {}", batch.size(), e.getMessage());
            }
        }
    }
}
