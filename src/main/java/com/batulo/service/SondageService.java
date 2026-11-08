package com.batulo.service;

import com.batulo.service.model.QuestionInfoModel;
import com.batulo.service.model.SectionInfoModel;
import com.batulo.service.model.SondageInfoModel;
import com.batulo.service.model.SondageModel;
import com.batulo.service.model.SondageResumeModel;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Formulaires dynamiques (module 4.1) : CRUD du sondage, de ses sections et de ses questions.
 * <ul>
 *   <li>ADMIN : tous les sondages de son entreprise.</li>
 *   <li>GESTIONNAIRE : lit les sondages qu'il a créés ; crée et modifie seulement avec peutCreerSondages
 *   (ActionInterditeException PERMISSION_SONDAGES_REQUISE, 403).</li>
 *   <li>Sondage d'une autre entreprise ou d'un autre gestionnaire, section ou question hors du sondage :
 *   ResourceNotFoundException (404).</li>
 * </ul>
 * Toutes les opérations sur les sections et les questions renvoient le sondage complet.
 */
public interface SondageService {

    SondageModel creer(Long utilisateurId, SondageInfoModel sondage);

    /** Du plus récemment modifié au plus ancien. */
    Page<SondageResumeModel> lister(Long utilisateurId, int page, int taille);

    SondageModel detail(Long utilisateurId, Long sondageId);

    SondageModel modifier(Long utilisateurId, Long sondageId, SondageInfoModel sondage);

    /** Supprime le sondage avec ses sections, questions et options. */
    void supprimer(Long utilisateurId, Long sondageId);

    /** Ajoute la section à la fin. */
    SondageModel ajouterSection(Long utilisateurId, Long sondageId, SectionInfoModel section);

    SondageModel modifierSection(Long utilisateurId, Long sondageId, Long sectionId, SectionInfoModel section);

    /** Supprime la section et ses questions, puis renumérote les sections. */
    SondageModel supprimerSection(Long utilisateurId, Long sondageId, Long sectionId);

    /** @param ids tous les ids des sections du sondage, chacun une fois, dans le nouvel ordre */
    SondageModel ordonnerSections(Long utilisateurId, Long sondageId, List<Long> ids);

    /**
     * Ajoute la question à la fin de la section.
     *
     * @throws com.batulo.exception.RegleGestionException QUESTION_INVALIDE ou VALEUR_OPTION_EN_DOUBLE
     */
    SondageModel ajouterQuestion(Long utilisateurId, Long sondageId, Long sectionId, QuestionInfoModel question);

    /** Le type peut changer ; la liste d'options remplace l'ancienne (valeurs existantes conservées). */
    SondageModel modifierQuestion(Long utilisateurId, Long sondageId, Long questionId, QuestionInfoModel question);

    /** Supprime la question, puis renumérote les questions de sa section. */
    SondageModel supprimerQuestion(Long utilisateurId, Long sondageId, Long questionId);

    /** @param ids tous les ids des questions de la section, chacun une fois, dans le nouvel ordre */
    SondageModel ordonnerQuestions(Long utilisateurId, Long sondageId, Long sectionId, List<Long> ids);
}
