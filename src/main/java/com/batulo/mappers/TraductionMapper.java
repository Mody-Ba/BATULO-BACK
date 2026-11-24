package com.batulo.mappers;

import com.batulo.exception.RegleGestionException;
import com.batulo.service.Langues;
import com.batulo.service.model.CompletudeLangueModel;
import com.batulo.service.model.CompletudeLangueResponse;
import com.batulo.service.model.LanguesModel;
import com.batulo.service.model.LanguesRequest;
import com.batulo.service.model.TraductionsLangueModel;
import com.batulo.service.model.TraductionsLangueResponse;
import com.batulo.service.model.TraductionsRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mapper API des traductions : Request -> Model et Model -> Response, sans entité. */
@Component
public class TraductionMapper {

    public LanguesModel toModel(LanguesRequest req) {
        return new LanguesModel(Langues.lire(req.languePrincipale()), Langues.lire(req.languesSupplementaires()));
    }

    /** Clé -> traduction, dans l'ordre reçu ; une clé donnée deux fois : 400 TRADUCTION_INVALIDE. */
    public Map<String, String> toTextes(TraductionsRequest req) {
        Map<String, String> textes = new LinkedHashMap<>();
        for (TraductionsRequest.Texte texte : req.textes()) {
            String cle = texte.cle().trim();
            if (textes.containsKey(cle)) {
                throw new RegleGestionException("TRADUCTION_INVALIDE", "La clé « " + cle + " » est en double.");
            }
            textes.put(cle, texte.traduction());
        }
        return textes;
    }

    public TraductionsLangueResponse toResponse(TraductionsLangueModel m) {
        return new TraductionsLangueResponse(m.sondageId(), m.langue(), m.languePrincipale(), m.traduits(), m.total(),
                m.complet(), m.textes().stream()
                .map(t -> new TraductionsLangueResponse.Texte(t.cle(), t.element(), t.champ(), t.sectionId(),
                        t.questionId(), t.optionId(), t.source(), t.traduction(), t.longueurMax()))
                .toList());
    }

    public List<CompletudeLangueResponse> toResponses(List<CompletudeLangueModel> modeles) {
        return modeles.stream()
                .map(m -> new CompletudeLangueResponse(m.langue(), m.traduits(), m.total(), m.complet(),
                        m.manquants().stream()
                                .map(x -> new CompletudeLangueResponse.Manquant(x.cle(), x.source())).toList()))
                .toList();
    }
}
