package com.tutorbridge.platform.web;

import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.IdempotencyKey;
import com.tutorbridge.platform.api.Page;
import com.tutorbridge.platform.internal.NotificationService;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** listOwnNotifications, markNotificationRead (UC15, TX30, TB-21). The service keeps each caller to their own. */
@RestController
class NotificationController {

    private final NotificationService notifications;

    NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping("/api/v1/me/notifications")
    Page<NotificationService.Notification> list(@AuthenticationPrincipal Actor actor,
            @RequestParam(required = false) @Size(max = 500) String cursor,
            @RequestParam(required = false) Integer limit) {
        return notifications.list(actor, cursor, limit);
    }

    @PostMapping("/api/v1/notifications/{notificationId}/read")
    NotificationService.Notification markRead(@AuthenticationPrincipal Actor actor, @PathVariable UUID notificationId,
            IdempotencyKey key) {
        return notifications.markRead(actor, notificationId, key);
    }
}
