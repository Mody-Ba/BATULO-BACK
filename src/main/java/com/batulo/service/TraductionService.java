package com.batulo.service;

import com.batulo.enums.Langue;
import com.batulo.service.model.CompletudeLangueModel;
import com.batulo.service.model.LanguesModel;
import com.batulo.service.model.SondageModel;
import com.batulo.service.model.TraductionsLangueModel;

import java.util.List;
import java.util.Map;

/**
 * Langues et traductions du contenu d'un sondage. Lecture : comme le sondage (créateur, admin, gestionnaire
 * assigné) ; écriture : brouillon seulement, mêmes droits que la modification (409 SONDAGE_PUBLIE,
 * 403 ACTION_RESERVEE_CREATEUR).
 */
public interface TraductionService {

    /**
     * Langue principale et langues supplémentaires d'un brouillon. Changer la langue principale ne change que
     * l'étiquette ; les traductions d'une langue retirée sont gardées mais ignorées.
     *
     * @throws com.batulo.exception.RegleGestionException LANGUE_INVALIDE (langue en double, principale répétée)
     */
    SondageModel definirLangues(Long utilisateurId, Long sondageId, LanguesModel langues);

    /** Complétude de chaque langue supplémentaire, dans l'ordre choisi. */
    List<CompletudeLangueModel> completude(Long utilisateurId, Long sondageId);

    /**
     * Textes côte à côte pour une langue supplémentaire.
     *
     * @throws com.batulo.exception.RegleGestionException LANGUE_INVALIDE si ce n'est pas une langue supplémentaire
     */
    TraductionsLangueModel traductions(Long utilisateurId, Long sondageId, Langue langue);

    /**
     * Enregistre les traductions d'une langue en une fois : clé -> traduction (null ou vide : effacée) ; une clé
     * absente reste inchangée.
     *
     * @throws com.batulo.exception.RegleGestionException TRADUCTION_INVALIDE (clé inconnue, texte trop long),
     *                                                    LANGUE_INVALIDE
     */
    TraductionsLangueModel enregistrer(Long utilisateurId, Long sondageId, Langue langue, Map<String, String> textes);
}
