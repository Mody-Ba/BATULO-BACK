package com.batulo.service.mappers;

import com.batulo.entity.Notification;
import com.batulo.service.model.NotificationModel;
import org.springframework.stereotype.Component;

/**
 * Mapper DB : convertit uniquement l'entité JPA Notification en NotificationModel.
 * Ne doit jamais connaître NotificationResponse.
 */
@Component("dbNotificationMapper")
public class NotificationMapper {

    public NotificationModel toModel(Notification entity) {
        return new NotificationModel(
                entity.getId(),
                entity.getType(),
                entity.getTitre(),
                entity.getMessage(),
                entity.isLue(),
                entity.getDateCreation());
    }
}
