package com.batulo.service;

import com.batulo.entity.Question;
import com.batulo.entity.Section;
import com.batulo.enums.CombinaisonCondition;
import com.batulo.service.ConditionAffichage.Regle;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Seul endroit où les conditions d'affichage sont évaluées (collecte, réponses de test, et toute reprise future).
 * Sans état partagé ni accès à la base.
 *
 * <p>Les questions se parcourent dans l'ordre du formulaire ({@link Parcours}) : comme toute source est placée avant
 * sa cible, sa visibilité et sa réponse sont déjà connues. Une source masquée compte comme non répondue : sa réponse
 * éventuelle est ignorée. Une section masquée masque toutes ses questions.</p>
 *
 * <p>Sans réponse, une règle n'est vraie que pour NON_REPONDUE (DIFFERENT compris : « différent de Oui » suppose une
 * réponse). Les comparaisons portent sur des nombres (NUMERIQUE, ECHELLE) ou des dates ISO (DATE).</p>
 */
public final class EvaluateurConditions {

    private EvaluateurConditions() {
    }

    /** Nouveau parcours d'un formulaire, pour une réponse. */
    public static Parcours parcours() {
        return new Parcours();
    }

    /**
     * Parcours d'un formulaire dans l'ordre : pour chaque question, {@link #visible(Question)} puis, si elle est
     * visible et répondue, {@link #repondre(Question, JsonNode)} avec sa valeur (déjà validée et non vide).
     */
    public static final class Parcours {

        private final Map<String, JsonNode> reponses = new HashMap<>();
        private final Map<Section, Boolean> sections = new IdentityHashMap<>();

        private Parcours() {
        }

        public boolean visible(Question question) {
            boolean sectionVisible = sections.computeIfAbsent(question.getSection(),
                    s -> evaluer(ConditionAffichage.lire(s.getCondition()), reponses));
            return sectionVisible && evaluer(ConditionAffichage.lire(question.getCondition()), reponses);
        }

        /** Réponse retenue d'une question visible, visible des règles des questions suivantes. */
        public void repondre(Question question, JsonNode valeur) {
            if (valeur != null && !valeur.isNull()) {
                reponses.put(question.getCle(), valeur);
            }
        }
    }

    /**
     * @param reponses réponses retenues (questions visibles et répondues) par clé de question
     * @return true si la condition est absente ou vérifiée
     */
    public static boolean evaluer(ConditionAffichage condition, Map<String, JsonNode> reponses) {
        if (condition == null || condition.regles().isEmpty()) {
            return true;
        }
        boolean et = condition.combinaison() != CombinaisonCondition.OU;
        for (Regle regle : condition.regles()) {
            boolean vraie = evaluer(regle, reponses.get(regle.cle()));
            if (et && !vraie) {
                return false;
            }
            if (!et && vraie) {
                return true;
            }
        }
        return et;
    }

    /** @param reponse réponse retenue de la source ; null si elle n'est pas répondue ou masquée */
    static boolean evaluer(Regle regle, JsonNode reponse) {
        boolean repondue = reponse != null && !reponse.isNull();
        return switch (regle.operateur()) {
            case REPONDUE -> repondue;
            case NON_REPONDUE -> !repondue;
            case EGAL -> repondue && egal(reponse, regle.valeur());
            case DIFFERENT -> repondue && !egal(reponse, regle.valeur());
            case PARMI -> repondue && parmi(reponse, regle.valeur());
            case CONTIENT -> repondue && contient(reponse, regle.valeur());
            case SUPERIEUR -> repondue && signe(comparer(reponse, regle.valeur())) > 0;
            case INFERIEUR -> repondue && signe(comparer(reponse, regle.valeur())) < 0;
            case ENTRE -> repondue && entre(reponse, regle.valeur());
        };
    }

    /** [min, max], bornes comprises. */
    private static boolean entre(JsonNode reponse, JsonNode bornes) {
        if (bornes == null || bornes.size() != 2) {
            return false;
        }
        Integer min = comparer(reponse, bornes.get(0));
        Integer max = comparer(reponse, bornes.get(1));
        return min != null && max != null && min >= 0 && max <= 0;
    }

    private static boolean egal(JsonNode reponse, JsonNode valeur) {
        if (reponse.isBoolean() || valeur.isBoolean()) {
            return reponse.isBoolean() && valeur.isBoolean() && reponse.booleanValue() == valeur.booleanValue();
        }
        if (reponse.isNumber() || valeur.isNumber()) {
            Integer c = comparer(reponse, valeur);
            return c != null && c == 0;
        }
        if (reponse.isTextual() && valeur.isTextual()) {
            LocalDate a = date(reponse);
            LocalDate b = date(valeur);
            return a != null && b != null ? a.equals(b) : reponse.asText().equals(valeur.asText());
        }
        return false;
    }

    /** CHOIX_UNIQUE : l'option choisie est dans la liste ; CHOIX_MULTIPLE : l'une des options cochées y est. */
    private static boolean parmi(JsonNode reponse, JsonNode valeurs) {
        if (reponse.isArray()) {
            for (JsonNode choisie : reponse) {
                if (contient(valeurs, choisie)) {
                    return true;
                }
            }
            return false;
        }
        return contient(valeurs, reponse);
    }

    private static boolean contient(JsonNode liste, JsonNode valeur) {
        if (!liste.isArray() || !valeur.isTextual()) {
            return false;
        }
        for (JsonNode element : liste) {
            if (element.isTextual() && element.asText().equals(valeur.asText())) {
                return true;
            }
        }
        return false;
    }

    /** Signe d'une comparaison ; une comparaison impossible (null) ne vérifie ni > ni < : 0 avec un garde. */
    private static int signe(Integer comparaison) {
        return comparaison == null ? 0 : comparaison;
    }

    /**
     * Nombres ou dates ISO. Null si les deux valeurs ne se comparent pas (types différents, texte qui n'est pas une
     * date) : la règle est alors fausse.
     */
    private static Integer comparer(JsonNode reponse, JsonNode valeur) {
        if (reponse.isNumber() && valeur.isNumber()) {
            return Integer.signum(reponse.decimalValue().compareTo(valeur.decimalValue()));
        }
        LocalDate a = date(reponse);
        LocalDate b = date(valeur);
        return a != null && b != null ? Integer.signum(a.compareTo(b)) : null;
    }

    private static LocalDate date(JsonNode valeur) {
        if (!valeur.isTextual()) {
            return null;
        }
        try {
            return LocalDate.parse(valeur.asText());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
