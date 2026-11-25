package com.batulo.service;

import com.batulo.enums.StatutInvitation;
import com.batulo.service.model.ExportModel;
import com.batulo.service.model.InvitationInfoModel;
import com.batulo.service.model.InvitationModel;
import com.batulo.service.model.RapportEnvoiModel;
import com.batulo.service.model.RapportImportModel;
import com.batulo.service.model.ResumeInvitationsModel;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Invitations nominatives d'un sondage. Elles portent sur la lignée : {@code sondageId} est n'importe quelle de ses
 * versions. Accès : l'admin, ou le gestionnaire créateur d'une version ou assigné (sinon 404, comme le sondage).
 */
public interface InvitationService {

    Page<InvitationModel> lister(Long utilisateurId, Long sondageId, StatutInvitation statut, String recherche,
                                 int page, int taille);

    ResumeInvitationsModel resume(Long utilisateurId, Long sondageId);

    /** @throws com.batulo.exception.ConflitException INVITE_EXISTANT si le courriel est déjà invité */
    InvitationModel ajouter(Long utilisateurId, Long sondageId, InvitationInfoModel info);

    /** Refusé après la réponse (409 INVITE_A_REPONDU) ou la révocation (409 INVITATION_REVOQUEE). */
    InvitationModel modifier(Long utilisateurId, Long sondageId, Long invitationId, InvitationInfoModel info);

    /** Refusé après la réponse (409 INVITE_A_REPONDU). */
    void supprimer(Long utilisateurId, Long sondageId, Long invitationId);

    /** Le lien ne marche plus (410 INVITATION_REVOQUEE). Refusé après la réponse ; sans effet si déjà révoquée. */
    InvitationModel revoquer(Long utilisateurId, Long sondageId, Long invitationId);

    /** Import CSV (en-têtes prenom, nom, courriel, telephone, langue ; 1000 lignes au plus). */
    RapportImportModel importer(Long utilisateurId, Long sondageId, byte[] fichier);

    /** Reprend l'identité des invités d'un autre sondage visible de l'entreprise, sans statut ni lien. */
    RapportImportModel reprendre(Long utilisateurId, Long sondageId, Long sondageSourceId);

    /**
     * Invitation par courriel, en arrière-plan : la sélection, ou (liste vide) tous ceux qui n'ont jamais été invités.
     *
     * @throws com.batulo.exception.ConflitException SONDAGE_NON_OUVERT (ni ouvert ni planifié)
     * @throws com.batulo.exception.RessourceFermeeException SONDAGE_FERME (entreprise désactivée, abonnement)
     * @throws com.batulo.exception.LimiteDepasseeException LIMITE_ENVOIS (429)
     */
    RapportEnvoiModel envoyer(Long utilisateurId, Long sondageId, List<Long> invitationIds);

    /** Relance des invités sans réponse, au plus une fois par délai (24 h) et par personne. Mêmes erreurs. */
    RapportEnvoiModel relancer(Long utilisateurId, Long sondageId, List<Long> invitationIds);

    /** Suivi en CSV (UTF-8 avec BOM, « ; »). */
    ExportModel exporter(Long utilisateurId, Long sondageId);
}
