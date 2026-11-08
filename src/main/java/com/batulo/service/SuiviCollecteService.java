package com.batulo.service;

import com.batulo.service.model.LienSondageModel;
import com.batulo.service.model.ReponseModel;
import com.batulo.service.model.SuiviCollecteModel;
import org.springframework.data.domain.Page;

/**
 * Lien, QR code et suivi de la collecte, pour l'admin et les gestionnaires qui voient le sondage (créateurs ou
 * assignés). Lecture seule : permis même avec un abonnement expiré.
 */
public interface SuiviCollecteService {

    /**
     * Lien public, le même pour toutes les versions.
     *
     * @throws com.batulo.exception.ConflitException SONDAGE_BROUILLON : jamais publié, pas encore de lien
     */
    LienSondageModel lien(Long utilisateurId, Long sondageId);

    /** QR code PNG du lien public. */
    byte[] qrCode(Long utilisateurId, Long sondageId, int taille);

    SuiviCollecteModel suivi(Long utilisateurId, Long sondageId);

    /** Réponses à cette version, les plus récentes d'abord. */
    Page<ReponseModel> reponses(Long utilisateurId, Long sondageId, int page, int taille);
}
