package com.batulo.service;

import com.batulo.entity.OptionQuestion;
import com.batulo.entity.Question;
import com.batulo.entity.Section;
import com.batulo.entity.Sondage;
import com.batulo.enums.OperateurCondition;
import com.batulo.enums.TypeQuestion;
import com.batulo.exception.ConflitException;
import com.batulo.exception.RegleGestionException;
import com.batulo.service.ConditionAffichage.Regle;
import com.batulo.service.model.ConditionModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cohérence des conditions d'affichage d'un sondage.
 * <ul>
 *   <li>Une source est placée avant sa cible : dans une section précédente, ou plus haut dans la même section pour
 *   une question ; dans une section précédente pour une section. Aucune référence vers l'avant, donc aucun cycle.</li>
 *   <li>L'opérateur est permis pour le type de la source ({@link OperateurCondition#permis}) et la valeur est cohérente
 *   (option existante, désignée par sa valeur ; booléen ; nombre ; date AAAA-MM-JJ ; [min, max] pour ENTRE).</li>
 * </ul>
 * Poser une condition incohérente : 400 CONDITION_INVALIDE. Une modification du contenu (suppression, déplacement,
 * réorganisation, changement de type ou d'options) qui casserait une condition existante : 409 CONDITION_DEPENDANTE,
 * la condition n'est jamais retirée sans que l'utilisateur le demande.
 */
@Component
public class ValidateurConditions {

    public static final String CODE_INVALIDE = "CONDITION_INVALIDE";
    public static final String CODE_DEPENDANTE = "CONDITION_DEPENDANTE";

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    /** Place d'une question : rang de sa section et rang dans la section (à partir de 0). */
    private record Place(int section, int rang, Question question) {
    }

    /** Condition d'une question, validée, au format stocké (sources par clé). */
    public JsonNode pourQuestion(Sondage sondage, Question cible, ConditionModel demande) {
        Section section = cible.getSection();
        return normaliser(sondage, new Place(sondage.getSections().indexOf(section),
                section.getQuestions().indexOf(cible), cible), nom(cible), demande);
    }

    /** Condition d'une section, validée, au format stocké (sources par clé). */
    public JsonNode pourSection(Sondage sondage, Section cible, ConditionModel demande) {
        return normaliser(sondage, new Place(sondage.getSections().indexOf(cible), -1, null), nom(cible), demande);
    }

    private JsonNode normaliser(Sondage sondage, Place cible, String nomCible, ConditionModel demande) {
        Map<Long, Place> parId = new HashMap<>();
        places(sondage).forEach(p -> parId.put(p.question().getId(), p));
        List<Regle> regles = new ArrayList<>();
        for (int i = 0; i < demande.regles().size(); i++) {
            ConditionModel.Regle demandee = demande.regles().get(i);
            Place source = parId.get(demandee.questionId());
            try {
                if (source == null) {
                    throw new IllegalArgumentException("la question " + demandee.questionId()
                            + " n'existe pas dans ce sondage");
                }
                verifierPlace(source, cible, nomCible);
                regles.add(new Regle(source.question().getCle(), demandee.operateur(),
                        valeur(source.question(), demandee.operateur(), demandee.valeur())));
            } catch (IllegalArgumentException e) {
                throw new RegleGestionException(CODE_INVALIDE, "Règle " + (i + 1) + " : " + e.getMessage() + ".");
            }
        }
        return new ConditionAffichage(demande.combinaison(), regles).versJson();
    }

    /**
     * Revérifie toutes les conditions du sondage après une modification de son contenu (ordre, déplacement, type,
     * options) : 409 CONDITION_DEPENDANTE si l'une n'est plus valable.
     */
    public void verifier(Sondage sondage) {
        List<Place> places = places(sondage);
        Map<String, Place> parCle = new HashMap<>();
        places.forEach(p -> parCle.put(p.question().getCle(), p));
        for (int s = 0; s < sondage.getSections().size(); s++) {
            Section section = sondage.getSections().get(s);
            verifier(ConditionAffichage.lire(section.getCondition()), new Place(s, -1, null), nom(section), parCle);
        }
        for (Place place : places) {
            verifier(ConditionAffichage.lire(place.question().getCondition()), place, nom(place.question()), parCle);
        }
    }

    private void verifier(ConditionAffichage condition, Place cible, String nomCible, Map<String, Place> parCle) {
        if (condition == null) {
            return;
        }
        for (Regle regle : condition.regles()) {
            Place source = parCle.get(regle.cle());
            try {
                if (source == null) {
                    throw new IllegalArgumentException("sa question source n'existe plus");
                }
                verifierPlace(source, cible, nomCible);
                valeur(source.question(), regle.operateur(), regle.valeur());
            } catch (IllegalArgumentException e) {
                throw new ConflitException(CODE_DEPENDANTE, "La condition d'affichage de " + nomCible
                        + " ne serait plus valable : " + e.getMessage() + ". Retirez ou modifiez d'abord cette condition.");
            }
        }
    }

    /**
     * Avant de supprimer des questions (ou une section entière) : aucune condition restante ne doit s'appuyer sur
     * elles, sinon 409 CONDITION_DEPENDANTE avec le nom de la condition à retirer d'abord.
     *
     * @param sectionRetiree section supprimée avec ses questions ; null pour une question seule
     */
    public void verifierRetrait(Sondage sondage, Section sectionRetiree, Collection<Question> questionsRetirees) {
        Map<String, Question> retirees = questionsRetirees.stream()
                .collect(Collectors.toMap(Question::getCle, q -> q, (a, b) -> a));
        for (Section section : sondage.getSections()) {
            if (section == sectionRetiree) {
                continue;
            }
            verifierRetrait(section.getCondition(), nom(section), retirees);
            for (Question question : section.getQuestions()) {
                if (!questionsRetirees.contains(question)) {
                    verifierRetrait(question.getCondition(), nom(question), retirees);
                }
            }
        }
    }

    private static void verifierRetrait(JsonNode json, String nomCible, Map<String, Question> retirees) {
        ConditionAffichage condition = ConditionAffichage.lire(json);
        if (condition == null) {
            return;
        }
        for (Regle regle : condition.regles()) {
            Question source = retirees.get(regle.cle());
            if (source != null) {
                throw new ConflitException(CODE_DEPENDANTE, "La question " + nom(source)
                        + " est utilisée par la condition d'affichage de " + nomCible
                        + " : retirez d'abord cette condition.");
            }
        }
    }

    // ---------------------------------------------------------------- Règles

    private static void verifierPlace(Place source, Place cible, String nomCible) {
        if (cible.rang() < 0) {
            if (source.section() >= cible.section()) {
                throw new IllegalArgumentException("la question source " + nom(source.question())
                        + " doit être dans une section placée avant " + nomCible);
            }
            return;
        }
        if (source.question() == cible.question()) {
            throw new IllegalArgumentException("une question ne peut pas dépendre d'elle-même");
        }
        boolean avant = source.section() < cible.section()
                || source.section() == cible.section() && source.rang() < cible.rang();
        if (!avant) {
            throw new IllegalArgumentException("la question source " + nom(source.question())
                    + " doit être placée avant " + nomCible
                    + " (dans une section précédente, ou plus haut dans la même section)");
        }
    }

    /** Valeur de la règle, vérifiée et normalisée (dates ISO, liste sans doublon) ; null pour REPONDUE, NON_REPONDUE. */
    private static JsonNode valeur(Question source, OperateurCondition operateur, JsonNode valeur) {
        TypeQuestion type = source.getType();
        Set<OperateurCondition> permis = OperateurCondition.permis(type);
        if (!permis.contains(operateur)) {
            throw new IllegalArgumentException("l'opérateur " + operateur + " ne s'applique pas à " + nom(source)
                    + " (" + type + ") ; opérateurs possibles : " + permis);
        }
        boolean absente = valeur == null || valeur.isNull();
        if (operateur.sansValeur()) {
            if (!absente) {
                throw new IllegalArgumentException("l'opérateur " + operateur + " ne prend pas de valeur");
            }
            return null;
        }
        if (absente) {
            throw new IllegalArgumentException("une valeur est obligatoire avec l'opérateur " + operateur);
        }
        return switch (type) {
            case CHOIX_UNIQUE, CHOIX_MULTIPLE -> operateur == OperateurCondition.PARMI
                    ? options(source, valeur) : JSON.textNode(option(source, valeur));
            case OUI_NON -> {
                if (!valeur.isBoolean()) {
                    throw new IllegalArgumentException("la valeur doit être true (oui) ou false (non)");
                }
                yield valeur;
            }
            case NUMERIQUE, ECHELLE -> operateur == OperateurCondition.ENTRE
                    ? bornes(valeur, ValidateurConditions::nombre) : nombre(valeur);
            case DATE -> operateur == OperateurCondition.ENTRE
                    ? bornes(valeur, ValidateurConditions::date) : date(valeur);
            default -> throw new IllegalArgumentException("l'opérateur " + operateur + " ne s'applique pas à "
                    + nom(source));
        };
    }

    /** Valeur (jamais le libellé) d'une option existante de la source. */
    private static String option(Question source, JsonNode valeur) {
        if (!valeur.isTextual()) {
            throw new IllegalArgumentException("la valeur doit être la valeur d'une option (texte)");
        }
        Set<String> valeurs = source.getOptions().stream().map(OptionQuestion::getValeur).collect(Collectors.toSet());
        if (!valeurs.contains(valeur.asText())) {
            throw new IllegalArgumentException("l'option « " + valeur.asText() + " » n'existe pas dans "
                    + nom(source) + " (valeurs possibles : " + valeurs + ")");
        }
        return valeur.asText();
    }

    private static ArrayNode options(Question source, JsonNode valeur) {
        if (!valeur.isArray() || valeur.isEmpty()) {
            throw new IllegalArgumentException("PARMI attend une liste non vide de valeurs d'options");
        }
        Set<String> vues = new LinkedHashSet<>();
        for (JsonNode element : valeur) {
            vues.add(option(source, element));
        }
        ArrayNode liste = JSON.arrayNode();
        vues.forEach(liste::add);
        return liste;
    }

    private interface Lecteur {
        JsonNode lire(JsonNode valeur);
    }

    /** [min, max] avec min <= max. */
    private static ArrayNode bornes(JsonNode valeur, Lecteur lecteur) {
        if (!valeur.isArray() || valeur.size() != 2) {
            throw new IllegalArgumentException("ENTRE attend deux bornes [min, max]");
        }
        JsonNode min = lecteur.lire(valeur.get(0));
        JsonNode max = lecteur.lire(valeur.get(1));
        boolean ordre = min.isNumber() ? min.decimalValue().compareTo(max.decimalValue()) <= 0
                : !LocalDate.parse(min.asText()).isAfter(LocalDate.parse(max.asText()));
        if (!ordre) {
            throw new IllegalArgumentException("la première borne de ENTRE doit être inférieure ou égale à la seconde");
        }
        ArrayNode bornes = JSON.arrayNode();
        bornes.add(min);
        bornes.add(max);
        return bornes;
    }

    private static JsonNode nombre(JsonNode valeur) {
        if (!valeur.isNumber()) {
            throw new IllegalArgumentException("la valeur doit être un nombre");
        }
        return valeur;
    }

    private static JsonNode date(JsonNode valeur) {
        try {
            if (valeur.isTextual()) {
                return JSON.textNode(LocalDate.parse(valeur.asText()).toString());
            }
        } catch (DateTimeParseException e) {
            // message ci-dessous
        }
        throw new IllegalArgumentException("la valeur doit être une date au format AAAA-MM-JJ");
    }

    // ---------------------------------------------------------------- Outils

    private static List<Place> places(Sondage sondage) {
        List<Place> places = new ArrayList<>();
        for (int s = 0; s < sondage.getSections().size(); s++) {
            List<Question> questions = sondage.getSections().get(s).getQuestions();
            for (int q = 0; q < questions.size(); q++) {
                places.add(new Place(s, q, questions.get(q)));
            }
        }
        return places;
    }

    private static String nom(Question question) {
        return "« " + question.getLibelle() + " »";
    }

    private static String nom(Section section) {
        return "la section « " + section.getTitre() + " »";
    }
}
