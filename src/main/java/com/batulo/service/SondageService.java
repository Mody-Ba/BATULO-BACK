package com.batulo.service;

import com.batulo.enums.StatutSondage;
import com.fasterxml.jackson.databind.JsonNode;
import com.batulo.service.model.ApercuModel;
import com.batulo.service.model.CreationSondageModel;
import com.batulo.service.model.DatesSondageModel;
import com.batulo.service.model.FormulaireModel;
import com.batulo.service.model.QuestionInfoModel;
import com.batulo.service.model.SectionInfoModel;
import com.batulo.service.model.SondageInfoModel;
import com.batulo.service.model.SondageModel;
import com.batulo.service.model.SondageResumeModel;
import com.batulo.service.model.VersionModel;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Formulaires dynamiques (module 4.1) : CRUD du sondage, de ses sections et de ses questions.
 * <ul>
 *   <li>ADMIN : tous les sondages de son entreprise.</li>
 *   <li>GESTIONNAIRE : voit une lignée s'il a créé l'une de ses versions ou si elle lui est assignée (GE-04) ;
 *   ne gère (modifie, publie, ferme, versionne) que les versions qu'il a créées, avec peutCreerSondages
 *   (PERMISSION_SONDAGES_REQUISE ou ACTION_RESERVEE_CREATEUR, 403).</li>
 *   <li>Sondage d'une autre entreprise ou d'un autre gestionnaire, section ou question hors du sondage :
 *   ResourceNotFoundException (404).</li>
 * </ul>
 * Toutes les opérations sur les sections et les questions renvoient le sondage complet.
 * <p>
 * Cycle de vie (étape 2.2) : seul un brouillon est modifiable ; un sondage publié est gelé (ConflitException
 * SONDAGE_PUBLIE, 409) et se modifie par une nouvelle version. Le statut (NON_COMMENCE, EN_COURS, TERMINE) se calcule
 * à partir des dates. Un brouillon se supprime ; un sondage publié se ferme mais ne se supprime pas.
 */
public interface SondageService {

    /**
     * Crée un brouillon vide, ou à partir d'un formulaire visible de l'entreprise (quel que soit son statut) :
     * copie des sections, questions et options avec les mêmes valeurs d'options, sans dates, publication,
     * assignations ni réponses. La source n'est jamais modifiée ; le nouveau sondage garde sondageOrigineId.
     */
    SondageModel creer(Long utilisateurId, CreationSondageModel creation);

    /**
     * Formulaires réutilisables : tous ceux de l'entreprise pour l'admin, ceux qu'il voit pour le gestionnaire
     * (avec la permission de créer des sondages). Modèles en premier.
     */
    Page<FormulaireModel> formulaires(Long utilisateurId, boolean modelesSeulement, int page, int taille);

    /** Marque ou démarque un sondage comme modèle (permis quel que soit son statut). */
    SondageModel marquerModele(Long utilisateurId, Long sondageId, boolean estModele);

    /**
     * Du plus récemment modifié au plus ancien ; statut null : tous.
     *
     * @param derniereVersion true : une ligne par lignée, la dernière version démarrée (EN_COURS ou TERMINE) si
     *                        elle existe, sinon la dernière version ; le statut filtre alors cette ligne
     */
    Page<SondageResumeModel> lister(Long utilisateurId, StatutSondage statut, boolean derniereVersion, int page,
                                    int taille);

    SondageModel detail(Long utilisateurId, Long sondageId);

    SondageModel modifier(Long utilisateurId, Long sondageId, SondageInfoModel sondage);

    /** Supprime un brouillon avec ses sections, questions et options (409 SONDAGE_PUBLIE s'il est publié). */
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

    /**
     * Marque (ou démarque) une question comme critère d'analyse et règle son découpage (tranches, granularité).
     * Réglage d'analyse : permis quel que soit le statut, même après publication.
     *
     * @throws com.batulo.exception.RegleGestionException QUESTION_INVALIDE (type non éligible, tranches invalides)
     */
    SondageModel definirCritere(Long utilisateurId, Long sondageId, Long questionId, boolean estCritere,
                                JsonNode critereConfig);

    /** Supprime la question, puis renumérote les questions de sa section. */
    SondageModel supprimerQuestion(Long utilisateurId, Long sondageId, Long questionId);

    /** @param ids tous les ids des questions de la section, chacun une fois, dans le nouvel ordre */
    SondageModel ordonnerQuestions(Long utilisateurId, Long sondageId, Long sectionId, List<Long> ids);

    /**
     * Déplace une question vers une section du même sondage (ou la repositionne dans sa section), brouillon seulement.
     * La question garde son id, sa clé, ses options et son critère ; les deux sections sont renumérotées.
     *
     * @param position rang dans la section d'arrivée, à partir de 1 ; null : à la fin
     * @throws com.batulo.exception.RegleGestionException POSITION_INVALIDE, LIMITE_ATTEINTE
     */
    SondageModel deplacerQuestion(Long utilisateurId, Long sondageId, Long questionId, Long sectionId,
                                  Integer position);

    /**
     * Publie un brouillon : ouverture maintenant ou planifiée, fermeture facultative. Ferme les versions
     * précédentes encore ouvertes au moment où celle-ci s'ouvre.
     *
     * @throws com.batulo.exception.RegleGestionException SONDAGE_INCOMPLET (aucune question, section vide)
     *         ou DATES_INVALIDES
     */
    SondageModel publier(Long utilisateurId, Long sondageId, DatesSondageModel dates);

    /** Ajuste les dates d'un sondage publié non terminé (l'ouverture seulement si elle n'est pas atteinte). */
    SondageModel modifierDates(Long utilisateurId, Long sondageId, DatesSondageModel dates);

    /** Fermeture manuelle, définitive : le sondage passe à TERMINE. */
    SondageModel fermer(Long utilisateurId, Long sondageId);

    /** Le formulaire tel que le verra le répondant (aussi pour un brouillon). */
    ApercuModel apercu(Long utilisateurId, Long sondageId);

    /** Copie indépendante : nouveau brouillon « Copie de ... », version 1, créé par l'appelant. */
    SondageModel dupliquer(Long utilisateurId, Long sondageId);

    /**
     * Brouillon version N+1 de la même lignée, à partir de la dernière version publiée.
     *
     * @throws com.batulo.exception.ConflitException SONDAGE_BROUILLON, BROUILLON_EXISTANT ou VERSION_OBSOLETE
     */
    SondageModel nouvelleVersion(Long utilisateurId, Long sondageId);

    /** Versions de la lignée visibles par l'appelant, de la plus ancienne à la plus récente. */
    List<VersionModel> versions(Long utilisateurId, Long sondageId);
}
