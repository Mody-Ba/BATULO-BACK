package com.batulo.service.mappers;

import com.batulo.entity.OptionQuestion;
import com.batulo.entity.Question;
import com.batulo.entity.Section;
import com.batulo.entity.Sondage;
import com.batulo.entity.Utilisateur;
import com.batulo.service.RegleSondage;
import com.batulo.service.model.ApercuModel;
import com.batulo.service.model.AuteurModel;
import com.batulo.service.model.FormulaireModel;
import com.batulo.service.model.FormulairePublicModel;
import com.batulo.service.model.OptionModel;
import com.batulo.service.model.QuestionModel;
import com.batulo.service.model.SectionModel;
import com.batulo.service.model.SondageModel;
import com.batulo.service.model.GestionnaireAssigneModel;
import com.batulo.service.model.SondageResumeModel;
import com.batulo.service.model.VersionModel;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

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
                auteur(entity.getCreePar()),
                entity.getDateCreation(),
                entity.getDateModification(),
                sections(entity));
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

    /** Formulaire du répondant : contenu de la version ouverte, sans aucune donnée interne. */
    public FormulairePublicModel toFormulairePublicModel(Sondage version) {
        return new FormulairePublicModel(
                version.getTitre(),
                version.getDescription(),
                version.isReponseUnique(),
                version.getDateFermeture(),
                sections(version));
    }

    public ApercuModel toApercuModel(Sondage entity, Instant maintenant) {
        return new ApercuModel(
                entity.getId(),
                entity.getTitre(),
                entity.getDescription(),
                entity.getVersion(),
                RegleSondage.statut(entity, maintenant),
                sections(entity));
    }

    private List<SectionModel> sections(Sondage entity) {
        return entity.getSections().stream().map(this::toModel).toList();
    }

    private static Long origine(Sondage entity) {
        return entity.getSondageOrigine() == null ? null : entity.getSondageOrigine().getId();
    }

    private SectionModel toModel(Section entity) {
        return new SectionModel(
                entity.getId(),
                entity.getTitre(),
                entity.getDescription(),
                entity.getOrdre(),
                entity.getQuestions().stream().map(this::toModel).toList());
    }

    private QuestionModel toModel(Question entity) {
        return new QuestionModel(
                entity.getId(),
                entity.getLibelle(),
                entity.getAide(),
                entity.getType(),
                entity.isObligatoire(),
                entity.getOrdre(),
                entity.getConfiguration().deepCopy(),
                entity.getOptions().stream().map(this::toModel).toList(),
                entity.isEstCritere(),
                entity.getCritereConfig() == null ? null : entity.getCritereConfig().deepCopy());
    }

    private OptionModel toModel(OptionQuestion entity) {
        return new OptionModel(entity.getId(), entity.getLibelle(), entity.getValeur(), entity.getOrdre());
    }

    private static AuteurModel auteur(Utilisateur utilisateur) {
        return utilisateur == null ? null
                : new AuteurModel(utilisateur.getId(), utilisateur.getPrenom(), utilisateur.getNom());
    }
}
