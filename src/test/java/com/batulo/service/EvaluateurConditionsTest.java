package com.batulo.service;

import com.batulo.entity.Question;
import com.batulo.entity.Section;
import com.batulo.enums.CombinaisonCondition;
import com.batulo.enums.OperateurCondition;
import com.batulo.enums.TypeQuestion;
import com.batulo.service.ConditionAffichage.Regle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.batulo.enums.OperateurCondition.CONTIENT;
import static com.batulo.enums.OperateurCondition.DIFFERENT;
import static com.batulo.enums.OperateurCondition.EGAL;
import static com.batulo.enums.OperateurCondition.ENTRE;
import static com.batulo.enums.OperateurCondition.INFERIEUR;
import static com.batulo.enums.OperateurCondition.NON_REPONDUE;
import static com.batulo.enums.OperateurCondition.PARMI;
import static com.batulo.enums.OperateurCondition.REPONDUE;
import static com.batulo.enums.OperateurCondition.SUPERIEUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/** Évaluation des conditions d'affichage : chaque opérateur, ET / OU, sections, sources masquées. */
class EvaluateurConditionsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode j(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException(json, e);
        }
    }

    /** Opérateur, valeur de la règle, réponse de la source (null : non répondue), résultat attendu. */
    static Stream<Arguments> regles() {
        return Stream.of(
                // CHOIX_UNIQUE
                arguments(EGAL, "\"dakar\"", "\"dakar\"", true),
                arguments(EGAL, "\"dakar\"", "\"thies\"", false),
                arguments(DIFFERENT, "\"dakar\"", "\"thies\"", true),
                arguments(DIFFERENT, "\"dakar\"", "\"dakar\"", false),
                arguments(PARMI, "[\"dakar\", \"thies\"]", "\"thies\"", true),
                arguments(PARMI, "[\"dakar\", \"thies\"]", "\"kaolack\"", false),
                // CHOIX_MULTIPLE
                arguments(CONTIENT, "\"mil\"", "[\"arachide\", \"mil\"]", true),
                arguments(CONTIENT, "\"mil\"", "[\"arachide\"]", false),
                arguments(PARMI, "[\"mil\", \"mais\"]", "[\"arachide\", \"mais\"]", true),
                arguments(PARMI, "[\"mil\", \"mais\"]", "[\"arachide\"]", false),
                // OUI_NON
                arguments(EGAL, "true", "true", true),
                arguments(EGAL, "true", "false", false),
                arguments(DIFFERENT, "true", "false", true),
                // NUMERIQUE, ECHELLE (12 et 12.0 sont égaux)
                arguments(EGAL, "12", "12.0", true),
                arguments(DIFFERENT, "12", "13", true),
                arguments(SUPERIEUR, "3", "4", true),
                arguments(SUPERIEUR, "3", "3", false),
                arguments(INFERIEUR, "3", "2.5", true),
                arguments(INFERIEUR, "3", "3", false),
                arguments(ENTRE, "[1, 5]", "1", true),
                arguments(ENTRE, "[1, 5]", "5", true),
                arguments(ENTRE, "[1, 5]", "5.5", false),
                // DATE
                arguments(EGAL, "\"2026-07-14\"", "\"2026-07-14\"", true),
                arguments(SUPERIEUR, "\"2026-07-14\"", "\"2026-07-15\"", true),
                arguments(INFERIEUR, "\"2026-07-14\"", "\"2026-07-15\"", false),
                arguments(ENTRE, "[\"2026-07-01\", \"2026-07-31\"]", "\"2026-07-31\"", true),
                arguments(ENTRE, "[\"2026-07-01\", \"2026-07-31\"]", "\"2026-08-01\"", false),
                // Valeurs non comparables : jamais vraie (ni >, ni <).
                arguments(INFERIEUR, "3", "\"abc\"", false),
                arguments(SUPERIEUR, "3", "\"abc\"", false),
                // Répondue / non répondue (tous les types)
                arguments(REPONDUE, null, "\"texte\"", true),
                arguments(REPONDUE, null, null, false),
                arguments(NON_REPONDUE, null, null, true),
                arguments(NON_REPONDUE, null, "{\"L1\": \"C1\"}", false),
                // Source sans réponse : toute règle est fausse, sauf NON_REPONDUE (DIFFERENT compris).
                arguments(EGAL, "true", null, false),
                arguments(DIFFERENT, "true", null, false),
                arguments(PARMI, "[\"dakar\"]", null, false),
                arguments(CONTIENT, "\"mil\"", null, false),
                arguments(SUPERIEUR, "3", null, false),
                arguments(INFERIEUR, "3", null, false),
                arguments(ENTRE, "[1, 5]", null, false));
    }

    @ParameterizedTest(name = "{0} {1} sur {2} : {3}")
    @MethodSource("regles")
    void chaqueOperateur(OperateurCondition operateur, String valeur, String reponse, boolean attendu) {
        Regle regle = new Regle("source", operateur, valeur == null ? null : j(valeur));
        assertThat(EvaluateurConditions.evaluer(regle, reponse == null ? null : j(reponse))).isEqualTo(attendu);
    }

    @Test
    void combinaisonsEtOu() {
        List<Regle> regles = List.of(new Regle("a", EGAL, j("true")), new Regle("b", SUPERIEUR, j("2")));
        ConditionAffichage et = new ConditionAffichage(CombinaisonCondition.ET, regles);
        ConditionAffichage ou = new ConditionAffichage(CombinaisonCondition.OU, regles);

        Map<String, JsonNode> deuxVraies = Map.of("a", j("true"), "b", j("3"));
        Map<String, JsonNode> uneVraie = Map.of("a", j("true"), "b", j("1"));
        Map<String, JsonNode> aucune = Map.of("a", j("false"));

        assertThat(EvaluateurConditions.evaluer(et, deuxVraies)).isTrue();
        assertThat(EvaluateurConditions.evaluer(et, uneVraie)).isFalse();
        assertThat(EvaluateurConditions.evaluer(ou, uneVraie)).isTrue();
        assertThat(EvaluateurConditions.evaluer(ou, aucune)).isFalse();
        // Pas de condition : toujours affichée.
        assertThat(EvaluateurConditions.evaluer(null, Map.of())).isTrue();
    }

    @Test
    void parcoursSectionMasqueeEtSourceMasqueeEnChaine() {
        Section s1 = section(null);
        Question enfants = question(s1, "enfants", TypeQuestion.OUI_NON, null);
        Question combien = question(s1, "combien", TypeQuestion.NUMERIQUE, condition("enfants", EGAL, "true"));
        // Dépend d'une question elle-même conditionnelle.
        Question ecole = question(s1, "ecole", TypeQuestion.OUI_NON, condition("combien", SUPERIEUR, "0"));
        // Section entière masquée si pas d'enfants.
        Section s2 = section(condition("enfants", EGAL, "true"));
        Question age = question(s2, "age", TypeQuestion.NUMERIQUE, null);

        // Pas d'enfants : combien, ecole (source masquée = non répondue) et toute la section 2 sont masqués.
        EvaluateurConditions.Parcours sans = EvaluateurConditions.parcours();
        assertThat(sans.visible(enfants)).isTrue();
        sans.repondre(enfants, j("false"));
        assertThat(sans.visible(combien)).isFalse();
        assertThat(sans.visible(ecole)).isFalse();
        assertThat(sans.visible(age)).isFalse();

        // Avec enfants : tout est visible.
        EvaluateurConditions.Parcours avec = EvaluateurConditions.parcours();
        avec.repondre(enfants, j("true"));
        assertThat(avec.visible(combien)).isTrue();
        avec.repondre(combien, j("2"));
        assertThat(avec.visible(ecole)).isTrue();
        assertThat(avec.visible(age)).isTrue();
    }

    private static Section section(JsonNode condition) {
        Section section = new Section();
        section.setCondition(condition);
        return section;
    }

    private static Question question(Section section, String cle, TypeQuestion type, JsonNode condition) {
        Question question = new Question();
        question.setSection(section);
        question.setCle(cle);
        question.setType(type);
        question.setCondition(condition);
        section.getQuestions().add(question);
        return question;
    }

    private static JsonNode condition(String cle, OperateurCondition operateur, String valeur) {
        return new ConditionAffichage(CombinaisonCondition.ET, List.of(new Regle(cle, operateur, j(valeur)))).versJson();
    }
}
