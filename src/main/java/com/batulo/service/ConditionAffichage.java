package com.batulo.service;

import com.batulo.enums.CombinaisonCondition;
import com.batulo.enums.OperateurCondition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Condition d'affichage d'une question ou d'une section, telle qu'elle est stockée (JSONB) :
 * {"combinaison": "ET"|"OU", "regles": [{"question": "<cle de la source>", "operateur": "EGAL", "valeur": ...}]}.
 * La source est désignée par sa clé stable : une copie ou une nouvelle version garde ses références telles quelles.
 */
public record ConditionAffichage(CombinaisonCondition combinaison, List<Regle> regles) {

    /** @param valeur null pour REPONDUE et NON_REPONDUE ; liste pour PARMI ; [min, max] pour ENTRE */
    public record Regle(String cle, OperateurCondition operateur, JsonNode valeur) {
    }

    /** Lit le JSON stocké ; null si la cible n'a pas de condition. */
    public static ConditionAffichage lire(JsonNode json) {
        if (json == null || json.isNull()) {
            return null;
        }
        List<Regle> regles = new ArrayList<>();
        for (JsonNode regle : json.path("regles")) {
            JsonNode valeur = regle.get("valeur");
            regles.add(new Regle(regle.path("question").asText(),
                    OperateurCondition.valueOf(regle.path("operateur").asText()),
                    valeur == null || valeur.isNull() ? null : valeur));
        }
        return new ConditionAffichage(CombinaisonCondition.valueOf(json.path("combinaison").asText("ET")), regles);
    }

    public ObjectNode versJson() {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put("combinaison", combinaison.name());
        ArrayNode liste = json.putArray("regles");
        for (Regle regle : regles) {
            ObjectNode r = liste.addObject();
            r.put("question", regle.cle());
            r.put("operateur", regle.operateur().name());
            if (regle.valeur() != null) {
                r.set("valeur", regle.valeur().deepCopy());
            }
        }
        return json;
    }
}
