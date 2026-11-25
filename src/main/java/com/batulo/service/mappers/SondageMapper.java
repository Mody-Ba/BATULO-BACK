package com.batulo.service.mappers;

import com.batulo.entity.OptionQuestion;
import com.batulo.entity.Question;
import com.batulo.entity.Section;
import com.batulo.entity.Sondage;
import com.batulo.entity.Utilisateur;
import com.batulo.enums.Langue;
import com.batulo.enums.TypeQuestion;
import com.batulo.service.ConditionAffichage;
import com.batulo.service.RegleSondage;
import com.batulo.service.Traducteur;
import com.batulo.service.model.ApercuModel;
import com.batulo.service.model.ApparenceModel;
import com.batulo.service.model.AuteurModel;
import com.batulo.service.model.ConditionModel;
import com.batulo.service.model.FormulaireModel;
import com.batulo.service.model.FormulairePublicModel;
import com.batulo.service.model.GestionnaireAssigneModel;
import com.batulo.service.model.InvitationPubliqueModel;
import com.batulo.service.model.LibellesMatriceModel;
import com.batulo.service.model.OptionModel;
import com.batulo.service.model.QuestionModel;
import com.batulo.service.model.SectionModel;
import com.batulo.service.model.SondageModel;
import com.batulo.service.model.SondageResumeModel;
import com.batulo.service.model.VersionModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapper DB : convertit uniquement les entités du sondage (Sondage, Section, Question, OptionQuestion)
 * en modèles. Ne doit jamais connaître les Response. Le statut est calculé par RegleSondage.
 */
@Component("dbSondageMapper")
public class SondageMapper {

    /** @param maintenant instant de référence pour le statut (calculé à partir des dates) */
    public SondageModel toModel(Sondage entity, Instant maintenant) {
        return new SondageModel(
                entity.getId(),
                entity.getEntreprise().getId(),
                entity.getTitre(),
                entity.getDescription(),
                RegleSondage.statut(entity, maintenant),
                entity.isPublie(),
                entity.getDatePublication(),
                entity.getDateFermeture(),
                entity.getVersion(),
                origine(entity),
                entity.isEstModele(),
                entity.isReponseUnique(),
                entity.getObjetEnquete(),
                entity.getTexteConfidentialite(),
                entity.isConsentementRequis(),
                entity.getTexteConsentement(),
                entity.getLanguePrincipale(),
                entity.getLanguesSupplementaires(),
                entity.isReponsesNominatives(),
                auteur(entity.getCreePar()),
                entity.getDateCreation(),
                entity.getDateModification(),
                sections(entity, Traducteur.principale(), false));
    }

    public SondageResumeModel toResumeModel(Sondage entity, Instant maintenant, long nombreSections,
                                            long nombreQuestions, long nombreReponses, long nombreReponsesLignee,
                                            List<GestionnaireAssigneModel> gestionnairesAssignes,
                                            Long brouillonEnPreparationId) {
        return new SondageResumeModel(
                entity.getId(),
                entity.getTitre(),
                entity.getDescription(),
                RegleSondage.statut(entity, maintenant),
                entity.isPublie(),
                entity.getDatePublication(),
                entity.getDateFermeture(),
                entity.getVersion(),
                entity.isEstModele(),
                auteur(entity.getCreePar()),
                nombreSections,
                nombreQuestions,
                nombreReponses,
                nombreReponsesLignee,
                gestionnairesAssignes,
                brouillonEnPreparationId != null,
                brouillonEnPreparationId,
                entity.getDateCreation(),
                entity.getDateModification());
    }

    public VersionModel toVersionModel(Sondage entity, Instant maintenant, long nombreReponses) {
        return new VersionModel(
                entity.getId(),
                entity.getVersion(),
                entity.getTitre(),
                RegleSondage.statut(entity, maintenant),
                entity.isPublie(),
                entity.getDatePublication(),
                entity.getDateFermeture(),
                entity.getDateCreation(),
                nombreReponses);
    }

    public FormulaireModel toFormulaireModel(Sondage entity, Instant maintenant, long nombreQuestions) {
        return new FormulaireModel(
                entity.getId(),
                entity.getTitre(),
                entity.isEstModele(),
                RegleSondage.statut(entity, maintenant),
                entity.getVersion(),
                nombreQuestions,
                entity.getDateCreation(),
                entity.getDateModification());
    }

    /**
     * Formulaire du répondant : contenu de la version ouverte dans la langue demandée (proposée par cette version,
     * sinon la langue principale ; texte principal en secours), sans aucune donnée interne.
     */
    public FormulairePublicModel toFormulairePublicModel(Sondage version, Boolean dejaRepondu, ApparenceModel apparence,
                                                         Langue langue, InvitationPubliqueModel invitation) {
        Traducteur t = Traducteur.pour(version, langue);
        return new FormulairePublicModel(
                t.titre(version),
                t.description(version),
                version.isReponseUnique(),
                version.getDateFermeture(),
                t.objetEnquete(version),
                t.texteConfidentialite(version),
                version.isConsentementRequis(),
                t.texteConsentement(version),
                apparence,
                t.langue(),
                version.getLanguePrincipale(),
                version.getLanguesDisponibles(),
                sections(version, t, true),
                dejaRepondu,
                invitation);
    }

    public ApercuModel toApercuModel(Sondage entity, Instant maintenant, ApparenceModel apparence, Langue langue) {
        Traducteur t = Traducteur.pour(entity, langue);
        return new ApercuModel(
                entity.getId(),
                t.titre(entity),
                t.description(entity),
                entity.getVersion(),
                RegleSondage.statut(entity, maintenant),
                t.objetEnquete(entity),
                t.texteConfidentialite(entity),
                entity.isConsentementRequis(),
                t.texteConsentement(entity),
                apparence,
                t.langue(),
                entity.getLanguePrincipale(),
                entity.getLanguesDisponibles(),
                sections(entity, t, true));
    }

    /** @param affichage true : formulaire public ou aperçu (libellés de matrice fournis) ; false : éditeur. */
    private List<SectionModel> sections(Sondage entity, Traducteur t, boolean affichage) {
        // Les règles désignent leur source par sa clé : on la traduit en id de question de cette version.
        Map<String, Long> ids = new HashMap<>();
        entity.getSections().forEach(s -> s.getQuestions().forEach(q -> ids.put(q.getCle(), q.getId())));
        return entity.getSections().stream().map(s -> toModel(s, ids, t, affichage)).toList();
    }

    private static ConditionModel condition(JsonNode json, Map<String, Long> ids) {
        ConditionAffichage condition = ConditionAffichage.lire(json);
        return condition == null ? null : new ConditionModel(condition.combinaison(), condition.regles().stream()
                .map(r -> new ConditionModel.Regle(ids.get(r.cle()), r.operateur(),
                        r.valeur() == null ? null : r.valeur().deepCopy()))
                .toList());
    }

    private static Long origine(Sondage entity) {
        return entity.getSondageOrigine() == null ? null : entity.getSondageOrigine().getId();
    }

    private SectionModel toModel(Section entity, Map<String, Long> ids, Traducteur t, boolean affichage) {
        return new SectionModel(
                entity.getId(),
                t.titre(entity),
                t.description(entity),
                entity.getOrdre(),
                condition(entity.getCondition(), ids),
                entity.getQuestions().stream().map(q -> toModel(q, ids, t, affichage)).toList());
    }

    private QuestionModel toModel(Question entity, Map<String, Long> ids, Traducteur t, boolean affichage) {
        JsonNode configuration = entity.getConfiguration().deepCopy();
        LibellesMatriceModel libellesMatrice = null;
        if (entity.getType() == TypeQuestion.ECHELLE && configuration instanceof ObjectNode echelle) {
            // Textes d'affichage seulement : traduits dans la configuration (les notes ne changent pas).
            for (String champ : List.of("libelleMin", "libelleMax")) {
                if (echelle.hasNonNull(champ)) {
                    echelle.put(champ, t.libelleEchelle(entity, champ));
                }
            }
        }
        if (affichage && entity.getType() == TypeQuestion.MATRICE) {
            // configuration.lignes / colonnes restent les valeurs à envoyer (texte principal) ; libellés à côté.
            List<String> lignes = new ArrayList<>();
            entity.getConfiguration().path("lignes").forEach(l -> lignes.add(t.matrice(entity, "lignes", l.asText())));
            List<String> colonnes = new ArrayList<>();
            entity.getConfiguration().path("colonnes")
                    .forEach(c -> colonnes.add(t.matrice(entity, "colonnes", c.asText())));
            libellesMatrice = new LibellesMatriceModel(lignes, colonnes);
        }
        return new QuestionModel(
                entity.getId(),
                t.libelle(entity),
                t.aide(entity),
                entity.getType(),
                entity.isObligatoire(),
                entity.getOrdre(),
                configuration,
                entity.getOptions().stream().map(o -> toModel(o, t)).toList(),
                entity.isEstCritere(),
                entity.getCritereConfig() == null ? null : entity.getCritereConfig().deepCopy(),
                condition(entity.getCondition(), ids),
                libellesMatrice);
    }

    private OptionModel toModel(OptionQuestion entity, Traducteur t) {
        return new OptionModel(entity.getId(), t.libelle(entity), entity.getValeur(), entity.getOrdre());
    }

    private static AuteurModel auteur(Utilisateur utilisateur) {
        return utilisateur == null ? null
                : new AuteurModel(utilisateur.getId(), utilisateur.getPrenom(), utilisateur.getNom());
    }
}
