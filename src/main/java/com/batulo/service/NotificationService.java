package com.batulo.service;

import com.batulo.enums.TypeNotification;
import com.batulo.service.model.NotificationModel;
import org.springframework.data.domain.Page;

/**
 * Notifications du tableau de bord (section 5 du cahier des charges, SA-05, AD-04).
 * Un utilisateur ne voit et ne modifie que ses propres notifications.
 */
public interface NotificationService {

    /** Notifications de l'utilisateur, les plus récentes d'abord. */
    Page<NotificationModel> lister(Long utilisateurId, int page, int taille);

    long nombreNonLues(Long utilisateurId);

    /** @throws com.batulo.exception.ResourceNotFoundException notification inconnue pour cet utilisateur */
    NotificationModel marquerLue(Long utilisateurId, Long notificationId);

    /** @return nombre de notifications passées de non lues à lues */
    int toutMarquerLu(Long utilisateurId);

    /** Crée une notification pour un utilisateur (usage interne des services). */
    void notifier(Long destinataireId, TypeNotification type, String titre, String message);

    /** Crée la même notification pour chaque super admin actif (usage interne des services). */
    void notifierSuperAdmins(TypeNotification type, String titre, String message);
}
