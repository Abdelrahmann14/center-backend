package com.center.push.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.center.push.entity.PushToken;

public interface PushTokenRepository extends JpaRepository<PushToken, UUID> {

    Optional<PushToken> findByToken(String token);

    List<PushToken> findByUserIdIn(Collection<UUID> userIds);

    void deleteByToken(String token);

    /**
     * Register a device token atomically: insert it, or (if the globally-unique
     * token already exists) re-point it at the current user. A single statement, so
     * two concurrent sign-ins on the same device can never race into a duplicate-key
     * error. Relies on the {@code gen_random_uuid()} default on the id column.
     */
    @Modifying
    @Query(value = "INSERT INTO push_tokens (user_id, token, platform, updated_at) "
            + "VALUES (:userId, :token, :platform, now()) "
            + "ON CONFLICT (token) DO UPDATE SET "
            + "user_id = EXCLUDED.user_id, platform = EXCLUDED.platform, updated_at = now()",
            nativeQuery = true)
    void upsert(@Param("userId") UUID userId, @Param("token") String token, @Param("platform") String platform);
}
