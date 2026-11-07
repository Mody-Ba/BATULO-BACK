package com.batulo.service.model;

import com.batulo.enums.TypeNotification;

import java.time.Instant;

public record NotificationModel(
        Long id,
        TypeNotification type,
        String titre,
        String message,
        boolean lue,
        Instant dateCreation
) {
}
