package com.center.push.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Sends Expo push notifications to devices registered against user accounts. */
public interface PushService {

    /** Register (upsert) a device's Expo push token for a user. */
    void register(UUID userId, String token, String platform);

    /** Forget a token (e.g. on logout or when Expo reports it invalid). */
    void unregister(String token);

    /**
     * Fan a push out to every device of the given users. Fire-and-forget and
     * best-effort: never throws into the caller, runs off the request thread.
     */
    void sendToUsers(Collection<UUID> userIds, String title, String body, Map<String, Object> data);
}
