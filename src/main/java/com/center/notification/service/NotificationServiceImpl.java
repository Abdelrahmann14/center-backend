package com.center.notification.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.common.util.PhotoCodec;
import com.center.notification.dto.NotificationResponse;
import com.center.notification.entity.Notification;
import com.center.notification.repository.NotificationRepository;
import com.center.notification.service.NotificationService;
import com.center.user.entity.User;
import com.center.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    /**
     * Most recent entries one inbox read returns.
     *
     * <p>The query was unbounded, and {@code notifications} is append-only: every
     * broadcast, every exam publish and every system event adds a row per
     * recipient and nothing ever removes them. Worse, each row in the response
     * repeats the sender's profile photo as a base64 data URL, so a year of one
     * teacher's broadcasts serialised the same ~50 KB image several hundred
     * times - tens of megabytes built in heap for a bell menu that shows a
     * preview list.
     *
     * <p>Two hundred newest is far more than the UI ever displays and turns an
     * inbox read into a fixed cost regardless of account age. Older entries stay
     * in the table; they are simply not shipped. A real archive view would need
     * a paged endpoint, which is a product decision, not a load fix.
     */
    private static final int INBOX_LIMIT = 200;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Override
    public void notify(UUID recipientUserId, String sender, String type, String title, String body, UUID linkId) {
        notify(recipientUserId, sender, type, title, body, linkId, null);
    }

    @Override
    public void notify(UUID recipientUserId, String sender, String type, String title, String body,
            UUID linkId, UUID outgoingId) {
        notifyFrom(recipientUserId, null, sender, type, title, body, linkId, outgoingId);
    }

    @Override
    public void notifyFrom(UUID recipientUserId, UUID senderUserId, String sender, String type,
            String title, String body, UUID linkId, UUID outgoingId) {
        Notification n = new Notification();
        n.setRecipientUserId(recipientUserId);
        n.setSenderUserId(senderUserId);
        n.setSender(sender);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setLinkId(linkId);
        n.setOutgoingId(outgoingId);
        notificationRepository.save(n);
    }

    @Override
    @Transactional
    public void deleteByLink(UUID linkId, java.util.Collection<String> types) {
        if (linkId == null || types == null || types.isEmpty()) {
            return;
        }
        notificationRepository.deleteByLinkIdAndTypeIn(linkId, types);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> list(UUID userId) {
        List<Notification> rows = notificationRepository
                .findByRecipientUserIdOrderByCreatedAtDesc(userId, Limit.of(INBOX_LIMIT));

        // Resolve each distinct sender's photo once (an inbox is usually all from
        // the same teacher), so the list costs one extra query at most.
        Set<UUID> senderIds = rows.stream()
                .map(Notification::getSenderUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> photos = senderIds.isEmpty() ? Map.of()
                : userRepository.findAllById(senderIds).stream()
                        .filter(u -> u.getPhotoData() != null)
                        .collect(Collectors.toMap(User::getId,
                                u -> PhotoCodec.toDataUrl(u.getPhotoData(), u.getPhotoType())));

        return rows.stream()
                .map(n -> new NotificationResponse(
                        n.getId(), n.getSender(),
                        n.getSenderUserId() == null ? null : photos.get(n.getSenderUserId()),
                        n.getType(), n.getTitle(), n.getBody(),
                        n.getLinkId(), n.isRead(), n.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByRecipientUserIdAndReadFalse(userId);
    }

    @Override
    @Transactional
    public void markRead(UUID id, UUID userId) {
        notificationRepository.findByIdAndRecipientUserId(id, userId).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }

    @Override
    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllRead(userId);
    }
}
