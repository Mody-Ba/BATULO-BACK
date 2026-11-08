package com.batulo.service.mappers;

import com.batulo.entity.OptionQuestion;
import com.batulo.entity.Question;
import com.batulo.entity.Section;
import com.batulo.entity.Sondage;
import com.batulo.entity.Utilisateur;
import com.batulo.service.model.AuteurModel;
import com.batulo.service.model.OptionModel;
import com.batulo.service.model.QuestionModel;
import com.batulo.service.model.SectionModel;
import com.batulo.service.model.SondageModel;
import com.batulo.service.model.SondageResumeModel;
import org.springframework.stereotype.Component;

/**
 * Mapper DB : convertit uniquement les entités du sondage (Sondage, Section, Question, OptionQuestion)
 * en modèles. Ne doit jamais connaître les Response.
 */
@Component("dbSondageMapper")
public class SondageMapper {

    public SondageModel toModel(Sondage entity) {
        return new SondageModel(
                entity.getId(),
                entity.getEntreprise().getId(),
                entity.getTitre(),
                entity.getDescription(),
                auteur(entity.getCreePar()),
                entity.getDateCreation(),
                entity.getDateModification(),
                entity.getSections().stream().map(this::toModel).toList());
    }

    public SondageResumeModel toResumeModel(Sondage entity, long nombreSections, long nombreQuestions) {
        return new SondageResumeModel(
                entity.getId(),
                entity.getTitre(),
                entity.getDescription(),
                auteur(entity.getCreePar()),
                nombreSections,
                nombreQuestions,
                entity.getDateCreation(),
                entity.getDateModification());
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
                entity.getOptions().stream().map(this::toModel).toList());
    }

    private OptionModel toModel(OptionQuestion entity) {
        return new OptionModel(entity.getId(), entity.getLibelle(), entity.getValeur(), entity.getOrdre());
    }

    private static AuteurModel auteur(Utilisateur utilisateur) {
        return utilisateur == null ? null
                : new AuteurModel(utilisateur.getId(), utilisateur.getPrenom(), utilisateur.getNom());
    }
}
