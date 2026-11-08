package com.batulo.service.mappers;

import com.batulo.entity.Question;
import com.batulo.entity.Reponse;
import com.batulo.entity.ReponseQuestion;
import com.batulo.service.model.ReponseModel;
import com.batulo.service.model.ValeurReponseModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapper DB : convertit uniquement l'entité Reponse (et ses lignes) en ReponseModel. Les valeurs reprennent le
 * format de l'envoi (valeur d'option, liste, booléen, nombre, texte, date, objet de matrice), dans l'ordre du
 * formulaire. Ne doit jamais connaître les Response.
 */
@Component("dbReponseMapper")
public class ReponseMapper {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    /** Ordre du formulaire : section, puis question. */
    private static final Comparator<Question> ORDRE_FORMULAIRE = Comparator
            .comparingInt((Question q) -> q.getSection().getOrdre())
            .thenComparingInt(Question::getOrdre);

    public ReponseModel toModel(Reponse entity) {
        Map<Question, List<ReponseQuestion>> parQuestion = new LinkedHashMap<>();
        entity.getValeurs().stream()
                .sorted(Comparator.comparing(ReponseQuestion::getQuestion, ORDRE_FORMULAIRE))
                .forEach(ligne -> parQuestion.computeIfAbsent(ligne.getQuestion(), q -> new ArrayList<>())
                        .add(ligne));
        List<ValeurReponseModel> valeurs = parQuestion.entrySet().stream()
                .map(e -> new ValeurReponseModel(e.getKey().getId(), e.getKey().getLibelle(), e.getKey().getType(),
                        valeur(e.getKey(), e.getValue())))
                .toList();
        return new ReponseModel(entity.getId(), entity.getDateSoumission(), valeurs);
    }

    private static JsonNode valeur(Question question, List<ReponseQuestion> lignes) {
        ReponseQuestion ligne = lignes.get(0);
        return switch (question.getType()) {
            case CHOIX_UNIQUE -> JSON.textNode(ligne.getOption().getValeur());
            case CHOIX_MULTIPLE -> {
                ArrayNode options = JSON.arrayNode();
                lignes.stream()
                        .map(ReponseQuestion::getOption)
                        .sorted(Comparator.comparingInt(o -> o.getOrdre()))
                        .forEach(o -> options.add(o.getValeur()));
                yield options;
            }
            case OUI_NON -> JSON.booleanNode(ligne.getValeurBooleenne());
            case ECHELLE, NUMERIQUE -> JSON.numberNode(nombre(ligne.getValeurNumerique()));
            case TEXTE_COURT, TEXTE_LONG -> JSON.textNode(ligne.getValeurTexte());
            case DATE -> JSON.textNode(ligne.getValeurDate().toString());
            case CLASSEMENT, MATRICE -> ligne.getValeurJson().deepCopy();
        };
    }

    /** 4.0000 donne 4, 12.50 donne 12.5 (jamais de notation scientifique). */
    private static BigDecimal nombre(BigDecimal valeur) {
        BigDecimal reduit = valeur.stripTrailingZeros();
        return reduit.scale() < 0 ? reduit.setScale(0) : reduit;
    }
}
