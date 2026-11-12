package com.batulo.service;

import com.batulo.service.model.EnvoiStatistiqueDemandeModel;
import com.batulo.service.model.EnvoiStatistiqueModel;
import com.batulo.service.model.ExportModel;
import com.batulo.service.model.StatistiquesModel;
import org.springframework.data.domain.Page;

/**
 * Envoi des statistiques d'un sondage terminé par un gestionnaire à son admin (GE-06, RG-04). L'admin les retrouve
 * dans « Statistiques reçues », recalculées avec les filtres enregistrés (les réponses d'un sondage terminé ne
 * changent plus), et peut télécharger le rapport PDF.
 */
public interface EnvoiStatistiqueService {

    /**
     * @throws com.batulo.exception.ConflitException SONDAGE_NON_TERMINE (la version, ou en lignée une version publiée)
     * @throws com.batulo.exception.RegleGestionException CRITERE_INVALIDE, PERIODE_INVALIDE
     */
    EnvoiStatistiqueModel envoyer(Long gestionnaireId, Long sondageId, EnvoiStatistiqueDemandeModel demande);

    /** Admin : statistiques reçues, les plus récentes d'abord. */
    Page<EnvoiStatistiqueModel> recues(Long adminId, int page, int taille);

    /** Gestionnaire : ses envois, les plus récents d'abord. */
    Page<EnvoiStatistiqueModel> envoyees(Long gestionnaireId, int page, int taille);

    /** Admin : les statistiques de l'envoi (marque l'envoi comme consulté). */
    StatistiquesModel statistiques(Long adminId, Long envoiId);

    /** Admin : le rapport PDF de l'envoi (étape 3.3). */
    ExportModel pdf(Long adminId, Long envoiId);
}
