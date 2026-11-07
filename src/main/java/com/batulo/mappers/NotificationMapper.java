package com.batulo.mappers;

import com.batulo.service.model.NombreNonLuesResponse;
import com.batulo.service.model.NotificationModel;
import com.batulo.service.model.NotificationResponse;
import com.batulo.service.model.NotificationsMarqueesResponse;
import com.batulo.service.model.PageResponse;
import com.batulo.service.model.ResultatTacheModel;
import com.batulo.service.model.ResultatTacheResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Mapper API : convertit uniquement Model -> Response.
 * Ne doit jamais connaître l'entité JPA Notification.
 */
@Component("apiNotificationMapper")
public class NotificationMapper {

    public NotificationResponse toResponse(NotificationModel model) {
        return new NotificationResponse(model.id(), model.type(), model.titre(), model.message(), model.lue(),
                model.dateCreation());
    }

    public PageResponse<NotificationResponse> toResponse(Page<NotificationModel> page) {
        return new PageResponse<>(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    public NombreNonLuesResponse toNombreNonLuesResponse(long nonLues) {
        return new NombreNonLuesResponse(nonLues);
    }

    public NotificationsMarqueesResponse toMarqueesResponse(int nombre) {
        return new NotificationsMarqueesResponse(nombre + " notification(s) marquée(s) comme lue(s).", nombre);
    }

    public ResultatTacheResponse toResponse(ResultatTacheModel model) {
        return new ResultatTacheResponse(
                "Tâche exécutée : " + model.rappelsEnvoyes() + " rappel(s) et " + model.abonnementsExpires()
                        + " expiration(s). Voir Mailpit (http://localhost:8025) et /api/notifications.",
                model.abonnementsVerifies(),
                model.rappelsEnvoyes(),
                model.abonnementsExpires());
    }
}
