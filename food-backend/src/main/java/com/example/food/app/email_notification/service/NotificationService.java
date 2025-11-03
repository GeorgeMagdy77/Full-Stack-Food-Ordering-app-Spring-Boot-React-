package com.example.food.app.email_notification.service;

import com.example.food.app.email_notification.dto.NotificationDTO;

public interface NotificationService {
    void sendEmail(NotificationDTO notificationDTO);
}
