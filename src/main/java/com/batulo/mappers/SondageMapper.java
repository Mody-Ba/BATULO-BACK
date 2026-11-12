package com.batulo.mappers;

import com.batulo.service.model.ApercuModel;
import com.batulo.service.model.ApercuResponse;
import com.batulo.service.model.AuteurModel;
import com.batulo.service.model.CreationSondageModel;
import com.batulo.service.model.CreationSondageRequest;
import com.batulo.service.model.FormulaireModel;
import com.batulo.service.model.FormulaireResponse;
import com.batulo.service.model.AuteurResponse;
import com.batulo.service.model.DatesSondageModel;
import com.batulo.service.model.DatesSondageRequest;
import com.batulo.service.model.OptionInfoModel;
import com.batulo.service.model.OptionModel;
import com.batulo.service.model.OptionRequest;
import com.batulo.service.model.OptionResponse;
import com.batulo.service.model.PageResponse;
import com.batulo.service.model.QuestionInfoModel;
import com.batulo.service.model.QuestionModel;
import com.batulo.service.model.QuestionRequest;
import com.batulo.service.model.QuestionResponse;
import com.batulo.service.model.SectionInfoModel;
import com.batulo.service.model.SectionModel;
import com.batulo.service.model.SectionRequest;
import com.batulo.service.model.SectionResponse;
import com.batulo.service.model.SondageInfoModel;
import com.batulo.service.model.SondageModel;
import com.batulo.service.model.SondageRequest;
import com.batulo.service.model.SondageResponse;
import com.batulo.service.model.GestionnaireAssigneResponse;
import com.batulo.service.model.SondageResumeModel;
import com.batulo.service.model.SondageResumeResponse;
import com.batulo.service.model.VersionModel;
import com.batulo.service.model.VersionResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mapper API : convertit uniquement Request -> Model et Model -> Response.
 * Ne doit jamais connaître les entités JPA du sondage.
 */
@Component("apiSondageMapper")
public class SondageMapper {

    public CreationSondageModel toModel(CreationSondageRequest req) {
        return new CreationSondageModel(req.titre(), req.description(), req.sondageSourceId(), req.reponseUnique());
    }

    public PageResponse<FormulaireResponse> toFormulaireResponses(Page<FormulaireModel> page) {
        return new PageResponse<>(
                page.getContent().stream()
                        .map(f -> new FormulaireResponse(f.id(), f.titre(), f.estModele(), f.statut(), f.version(),
                                f.nombreQuestions(), f.dateCreation(), f.dateModification()))
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    public SondageInfoModel toModel(SondageRequest req) {
        return new SondageInfoModel(req.titre(), req.description(), req.reponseUnique());
    }

    public SectionInfoModel toModel(SectionRequest req) {
        return new SectionInfoModel(req.titre(), req.description());
    }

    public QuestionInfoModel toModel(QuestionRequest req) {
        List<OptionInfoModel> options = req.options() == null ? List.of()
                : req.options().stream().map(this::toModel).toList();
        return new QuestionInfoModel(
                req.libelle(),
                req.aide(),
                req.type(),
                Boolean.TRUE.equals(req.obligatoire()),
                req.configuration(),
                options,
                req.estCritere(),
                req.critereConfig());
    }

    private OptionInfoModel toModel(OptionRequest req) {
        return new OptionInfoModel(req.id(), req.libelle(), req.valeur());
    }

    public SondageResponse toResponse(SondageModel model) {
        return new SondageResponse(
                model.id(),
                model.entrepriseId(),
                model.titre(),
                model.description(),
                model.statut(),
                model.publie(),
                model.datePublication(),
                model.dateFermeture(),
                model.version(),
                model.sondageOrigineId(),
                model.estModele(),
                model.reponseUnique(),
                toResponse(model.creePar()),
                model.dateCreation(),
                model.dateModification(),
                model.sections().stream().map(this::toResponse).toList());
    }

    public DatesSondageModel toModel(DatesSondageRequest req) {
        return req == null ? new DatesSondageModel(null, null)
                : new DatesSondageModel(req.datePublication(), req.dateFermeture());
    }

    public ApercuResponse toResponse(ApercuModel model) {
        return new ApercuResponse(model.id(), model.titre(), model.description(), model.version(), model.statut(),
                model.sections().stream().map(this::toResponse).toList());
    }

    public List<VersionResponse> toVersionResponses(List<VersionModel> versions) {
        return versions.stream()
                .map(v -> new VersionResponse(v.id(), v.version(), v.titre(), v.statut(), v.publie(),
                        v.datePublication(), v.dateFermeture(), v.dateCreation()))
                .toList();
    }

    public PageResponse<SondageResumeResponse> toResponse(Page<SondageResumeModel> page) {
        return new PageResponse<>(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    private SondageResumeResponse toResponse(SondageResumeModel model) {
        return new SondageResumeResponse(
                model.id(),
                model.titre(),
                model.description(),
                model.statut(),
                model.publie(),
                model.datePublication(),
                model.dateFermeture(),
                model.version(),
                model.estModele(),
                toResponse(model.creePar()),
                model.nombreSections(),
                model.nombreQuestions(),
                model.nombreReponses(),
                model.nombreReponsesLignee(),
                model.gestionnairesAssignes() == null ? null : model.gestionnairesAssignes().stream()
                        .map(g -> new GestionnaireAssigneResponse(g.id(), g.prenom(), g.nom(), g.actif())).toList(),
                model.brouillonEnPreparation(),
                model.brouillonEnPreparationId(),
                model.dateCreation(),
                model.dateModification());
    }

    private SectionResponse toResponse(SectionModel model) {
        return new SectionResponse(model.id(), model.titre(), model.description(), model.ordre(),
                model.questions().stream().map(this::toResponse).toList());
    }

    private QuestionResponse toResponse(QuestionModel model) {
        return new QuestionResponse(model.id(), model.libelle(), model.aide(), model.type(), model.obligatoire(),
                model.ordre(), model.configuration(), model.options().stream().map(this::toResponse).toList(),
                model.estCritere(), model.critereConfig());
    }

    private OptionResponse toResponse(OptionModel model) {
        return new OptionResponse(model.id(), model.libelle(), model.valeur(), model.ordre());
    }

    private AuteurResponse toResponse(AuteurModel model) {
        return model == null ? null : new AuteurResponse(model.id(), model.prenom(), model.nom());
    }
}
