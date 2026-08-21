package com.center.notification.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.notification.dto.NotificationResponse;
import com.center.notification.entity.Notification;
import com.center.notification.repository.NotificationRepository;
import com.center.notification.service.NotificationService;
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

    @Override
    public void notify(UUID recipientUserId, String sender, String type, String title, String body,
            UUID linkId) {
        Notification n = new Notification();
        n.setRecipientUserId(recipientUserId);
        n.setSender(sender);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setLinkId(linkId);
        notificationRepository.save(n);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> list(UUID userId) {
        return notificationRepository
                .findByRecipientUserIdOrderByCreatedAtDesc(userId, Limit.of(INBOX_LIMIT)).stream()
                .map(n -> new NotificationResponse(
                        n.getId(), n.getSender(), n.getType(), n.getTitle(), n.getBody(),
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
