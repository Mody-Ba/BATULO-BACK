package com.batulo.service;

import com.batulo.service.model.AssignationModel;
import com.batulo.service.model.SondageGestionnaireModel;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Assignation des sondages aux gestionnaires par l'admin (AD-10, RG-03). L'assignation porte sur la lignée :
 * l'id d'une version quelconque désigne le sondage et toutes ses versions. Tout se limite à l'entreprise de l'admin
 * (ResourceNotFoundException, 404, sinon).
 */
public interface AssignationService {

    /**
     * Assigne le sondage aux gestionnaires (sans doublon : un gestionnaire déjà assigné n'est pas notifié deux fois).
     * Notification et courriel à chaque nouveau gestionnaire.
     *
     * @return les gestionnaires assignés au sondage
     * @throws com.batulo.exception.ConflitException GESTIONNAIRE_DESACTIVE ou GESTIONNAIRE_CREATEUR
     */
    List<AssignationModel> assigner(Long adminId, Long sondageId, List<Long> gestionnaireIds);

    /** Retire l'assignation ; notification dans le tableau de bord du gestionnaire (sans courriel). */
    void retirer(Long adminId, Long sondageId, Long gestionnaireId);

    /** Gestionnaires assignés au sondage, du plus ancien au plus récent. */
    List<AssignationModel> gestionnairesDuSondage(Long adminId, Long sondageId);

    /** Sondages visibles par le gestionnaire (créés ou assignés), du plus récemment modifié au plus ancien. */
    Page<SondageGestionnaireModel> sondagesDuGestionnaire(Long adminId, Long gestionnaireId, int page, int taille);
}
