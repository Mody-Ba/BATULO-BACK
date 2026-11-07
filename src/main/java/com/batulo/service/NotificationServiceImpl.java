package com.batulo.service;

import com.batulo.entity.Notification;
import com.batulo.entity.Utilisateur;
import com.batulo.enums.Role;
import com.batulo.enums.TypeNotification;
import com.batulo.exception.ResourceNotFoundException;
import com.batulo.repository.NotificationRepository;
import com.batulo.repository.UtilisateurRepository;
import com.batulo.service.mappers.NotificationMapper;
import com.batulo.service.model.NotificationModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final NotificationMapper notificationMapper;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   UtilisateurRepository utilisateurRepository,
                                   NotificationMapper notificationMapper) {
        this.notificationRepository = notificationRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.notificationMapper = notificationMapper;
    }

    @Override
    public Page<NotificationModel> lister(Long utilisateurId, int page, int taille) {
        return notificationRepository.findByDestinataireId(utilisateurId,
                        PageRequest.of(page, taille, Sort.by(Sort.Direction.DESC, "dateCreation", "id")))
                .map(notificationMapper::toModel);
    }

    @Override
    public long nombreNonLues(Long utilisateurId) {
        return notificationRepository.countByDestinataireIdAndLueFalse(utilisateurId);
    }

    @Override
    @Transactional
    public NotificationModel marquerLue(Long utilisateurId, Long notificationId) {
        // Une notification d'un autre utilisateur donne 404, sans révéler qu'elle existe.
        Notification notification = notificationRepository.findByIdAndDestinataireId(notificationId, utilisateurId)
                .orElseThrow(() -> new ResourceNotFoundException("NOTIFICATION_INTROUVABLE",
                        "Aucune notification " + notificationId + " pour cet utilisateur."));
        notification.setLue(true);
        return notificationMapper.toModel(notification);
    }

    @Override
    @Transactional
    public int toutMarquerLu(Long utilisateurId) {
        return notificationRepository.marquerToutesLues(utilisateurId);
    }

    @Override
    @Transactional
    public void notifier(Long destinataireId, TypeNotification type, String titre, String message) {
        creer(utilisateurRepository.getReferenceById(destinataireId), type, titre, message);
    }

    @Override
    @Transactional
    public void notifierSuperAdmins(TypeNotification type, String titre, String message) {
        utilisateurRepository.findByRoleAndActifTrue(Role.SUPER_ADMIN)
                .forEach(superAdmin -> creer(superAdmin, type, titre, message));
    }

    private void creer(Utilisateur destinataire, TypeNotification type, String titre, String message) {
        Notification notification = new Notification();
        notification.setDestinataire(destinataire);
        notification.setType(type);
        notification.setTitre(titre);
        notification.setMessage(message);
        notification.setLue(false);
        notificationRepository.save(notification);
    }
}
