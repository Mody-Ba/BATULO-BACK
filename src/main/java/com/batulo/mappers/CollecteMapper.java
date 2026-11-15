package com.batulo.mappers;

import com.batulo.service.model.ConfirmationReponseResponse;
import com.batulo.service.model.FormulairePublicModel;
import com.batulo.service.model.FormulairePublicResponse;
import com.batulo.service.model.LienSondageModel;
import com.batulo.service.model.LienSondageResponse;
import com.batulo.service.model.OptionModel;
import com.batulo.service.model.OptionPubliqueResponse;
import com.batulo.service.model.PageResponse;
import com.batulo.service.model.QuestionModel;
import com.batulo.service.model.QuestionPubliqueResponse;
import com.batulo.service.model.ReponseModel;
import com.batulo.service.model.ReponseResponse;
import com.batulo.service.model.SectionModel;
import com.batulo.service.model.SectionPubliqueResponse;
import com.batulo.service.model.SoumissionModel;
import com.batulo.service.model.SoumissionRequest;
import com.batulo.service.model.SuiviCollecteModel;
import com.batulo.service.model.SuiviCollecteResponse;
import com.batulo.service.model.ValeurReponseResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Mapper API de la collecte : convertit uniquement Request -> Model et Model -> Response.
 * Ne doit jamais connaître les entités JPA.
 */
@Component("apiCollecteMapper")
public class CollecteMapper {

    public SoumissionModel toModel(SoumissionRequest req) {
        return new SoumissionModel(req.jetonRepondant(), req.consentement(), req.reponses().stream()
                .map(r -> new SoumissionModel.ValeurSoumise(r.questionId(), r.valeur()))
                .toList());
    }

    public FormulairePublicResponse toResponse(FormulairePublicModel model) {
        return new FormulairePublicResponse(model.titre(), model.description(), model.reponseUnique(),
                model.dateFermeture(), model.objetEnquete(), model.texteConfidentialite(), model.consentementRequis(),
                model.texteConsentement(), model.sections().stream().map(this::toResponse).toList(), model.dejaRepondu());
    }

    public ConfirmationReponseResponse toConfirmation(Instant dateSoumission) {
        return new ConfirmationReponseResponse("Merci, votre réponse a été enregistrée.", dateSoumission);
    }

    public LienSondageResponse toResponse(LienSondageModel model) {
        return new LienSondageResponse(model.jeton(), model.url());
    }

    public SuiviCollecteResponse toResponse(SuiviCollecteModel m) {
        return new SuiviCollecteResponse(m.sondageId(), m.version(), m.statut(), m.collecteOuverte(),
                m.raisonFermeture(), m.nombreReponses(), m.nombreReponsesTotal(), m.derniereReponse());
    }

    public PageResponse<ReponseResponse> toResponse(Page<ReponseModel> page) {
        return new PageResponse<>(
                page.getContent().stream()
                        .map(r -> new ReponseResponse(r.id(), r.sondageId(), r.version(), r.dateSoumission(),
                                r.dateConsentement(), r.valeurs().stream()
                                .map(v -> new ValeurReponseResponse(v.questionId(), v.libelle(), v.type(), v.valeur()))
                                .toList()))
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    private SectionPubliqueResponse toResponse(SectionModel model) {
        return new SectionPubliqueResponse(model.id(), model.titre(), model.description(), model.ordre(),
                model.questions().stream().map(this::toResponse).toList());
    }

    /** Réglages d'analyse internes (critère, découpage) : jamais montrés au répondant. */
    private QuestionPubliqueResponse toResponse(QuestionModel model) {
        return new QuestionPubliqueResponse(model.id(), model.libelle(), model.aide(), model.type(), model.obligatoire(),
                model.ordre(), model.configuration(), model.options().stream().map(this::toResponse).toList());
    }

    private OptionPubliqueResponse toResponse(OptionModel model) {
        return new OptionPubliqueResponse(model.id(), model.libelle(), model.valeur(), model.ordre());
    }
}
