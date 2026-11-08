package com.batulo.service;

import com.batulo.entity.OptionQuestion;
import com.batulo.entity.Question;
import com.batulo.entity.ReponseQuestion;
import com.batulo.exception.ErreurQuestion;
import com.batulo.exception.ReponsesInvalidesException;
import com.batulo.service.model.SoumissionModel.ValeurSoumise;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Valide les réponses d'un répondant contre les questions d'une version (type, configuration, options) et les
 * convertit en lignes {@link ReponseQuestion}, rangées dans la colonne de leur type pour les statistiques.
 * Toutes les erreurs sont collectées, une par question : 400 REPONSES_INVALIDES.
 */
@Component
public class ValidateurReponse {

    private final ObjectMapper objectMapper;

    public ValidateurReponse(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param questions toutes les questions de la version, dans l'ordre du formulaire
     * @return les lignes à enregistrer (aucune pour une question facultative sans réponse)
     */
    public List<ReponseQuestion> valider(List<Question> questions, List<ValeurSoumise> reponses) {
        Map<Long, Question> parId = new LinkedHashMap<>();
        questions.forEach(q -> parId.put(q.getId(), q));

        List<ErreurQuestion> erreurs = new ArrayList<>();
        Map<Long, JsonNode> valeurs = new LinkedHashMap<>();
        for (ValeurSoumise reponse : reponses) {
            if (!parId.containsKey(reponse.questionId())) {
                erreurs.add(new ErreurQuestion(reponse.questionId(), "Cette question n'existe pas dans ce sondage."));
            } else if (valeurs.containsKey(reponse.questionId())) {
                erreurs.add(new ErreurQuestion(reponse.questionId(), "Cette question a reçu deux réponses."));
            } else {
                valeurs.put(reponse.questionId(), reponse.valeur());
            }
        }

        List<ReponseQuestion> lignes = new ArrayList<>();
        for (Question question : parId.values()) {
            JsonNode valeur = valeurs.get(question.getId());
            try {
                List<ReponseQuestion> converties = estVide(valeur) ? List.of() : convertir(question, valeur);
                if (converties.isEmpty() && question.isObligatoire()) {
                    throw new IllegalArgumentException("Cette question est obligatoire.");
                }
                lignes.addAll(converties);
            } catch (IllegalArgumentException e) {
                erreurs.add(new ErreurQuestion(question.getId(), e.getMessage()));
            }
        }
        if (!erreurs.isEmpty()) {
            throw new ReponsesInvalidesException(erreurs);
        }
        return lignes;
    }

    /** Absente, null, texte vide, liste ou objet vide : pas de réponse. */
    private static boolean estVide(JsonNode valeur) {
        return valeur == null || valeur.isNull()
                || (valeur.isTextual() && valeur.asText().isBlank())
                || (valeur.isContainerNode() && valeur.isEmpty());
    }

    private List<ReponseQuestion> convertir(Question question, JsonNode valeur) {
        JsonNode config = question.getConfiguration();
        return switch (question.getType()) {
            case CHOIX_UNIQUE -> List.of(avecOption(question, option(question, texte(valeur, "une valeur d'option"))));
            case CHOIX_MULTIPLE -> choixMultiple(question, valeur);
            case OUI_NON -> {
                if (!valeur.isBoolean()) {
                    throw new IllegalArgumentException("La réponse doit être true (oui) ou false (non).");
                }
                ReponseQuestion ligne = new ReponseQuestion(question);
                ligne.setValeurBooleenne(valeur.booleanValue());
                yield List.of(ligne);
            }
            case TEXTE_COURT, TEXTE_LONG -> {
                String texte = texte(valeur, "un texte").trim();
                int longueurMax = config.path("longueurMax").asInt();
                if (texte.length() > longueurMax) {
                    throw new IllegalArgumentException("Le texte ne doit pas dépasser " + longueurMax + " caractères.");
                }
                ReponseQuestion ligne = new ReponseQuestion(question);
                ligne.setValeurTexte(texte);
                yield List.of(ligne);
            }
            case ECHELLE -> {
                int min = config.path("min").asInt();
                int max = config.path("max").asInt();
                if (!valeur.isIntegralNumber() || valeur.intValue() < min || valeur.intValue() > max) {
                    throw new IllegalArgumentException("La note doit être un entier de " + min + " à " + max + ".");
                }
                yield List.of(numerique(question, BigDecimal.valueOf(valeur.intValue())));
            }
            case NUMERIQUE -> List.of(numerique(question, nombre(valeur, config)));
            case DATE -> {
                LocalDate date = date(valeur, config);
                ReponseQuestion ligne = new ReponseQuestion(question);
                ligne.setValeurDate(date);
                yield List.of(ligne);
            }
            case CLASSEMENT -> List.of(avecJson(question, classement(question, valeur)));
            case MATRICE -> List.of(avecJson(question, matrice(valeur, config)));
        };
    }

    private List<ReponseQuestion> choixMultiple(Question question, JsonNode valeur) {
        if (!valeur.isArray()) {
            throw new IllegalArgumentException("La réponse doit être une liste de valeurs d'options.");
        }
        Set<String> vues = new HashSet<>();
        List<ReponseQuestion> lignes = new ArrayList<>();
        for (JsonNode element : valeur) {
            String code = texte(element, "une valeur d'option");
            if (!vues.add(code)) {
                throw new IllegalArgumentException("L'option « " + code + " » est choisie deux fois.");
            }
            lignes.add(avecOption(question, option(question, code)));
        }
        return lignes;
    }

    /** Toutes les options, chacune une seule fois, dans l'ordre choisi. */
    private ArrayNode classement(Question question, JsonNode valeur) {
        if (!valeur.isArray()) {
            throw new IllegalArgumentException("Le classement doit être la liste ordonnée des valeurs d'options.");
        }
        Set<String> attendues = new HashSet<>();
        question.getOptions().forEach(o -> attendues.add(o.getValeur()));
        Set<String> vues = new HashSet<>();
        ArrayNode ordre = objectMapper.createArrayNode();
        for (JsonNode element : valeur) {
            String code = texte(element, "une valeur d'option");
            option(question, code);
            if (!vues.add(code)) {
                throw new IllegalArgumentException("L'option « " + code + " » apparaît deux fois dans le classement.");
            }
            ordre.add(code);
        }
        if (!vues.equals(attendues)) {
            throw new IllegalArgumentException("Le classement doit contenir toutes les options ("
                    + attendues.size() + "), chacune une seule fois.");
        }
        return ordre;
    }

    /** Une colonne valide pour CHAQUE ligne de la matrice ; renvoyé dans l'ordre des lignes. */
    private ObjectNode matrice(JsonNode valeur, JsonNode config) {
        if (!valeur.isObject()) {
            throw new IllegalArgumentException("La réponse doit être un objet {\"ligne\": \"colonne\"}.");
        }
        List<String> lignes = new ArrayList<>();
        config.path("lignes").forEach(l -> lignes.add(l.asText()));
        Set<String> colonnes = new HashSet<>();
        config.path("colonnes").forEach(c -> colonnes.add(c.asText()));

        Iterator<String> cles = valeur.fieldNames();
        while (cles.hasNext()) {
            String cle = cles.next();
            if (!lignes.contains(cle)) {
                throw new IllegalArgumentException("La ligne « " + cle + " » n'existe pas dans cette matrice.");
            }
        }
        ObjectNode resultat = objectMapper.createObjectNode();
        for (String ligne : lignes) {
            JsonNode colonne = valeur.get(ligne);
            if (colonne == null || colonne.isNull() || !colonne.isTextual() || colonne.asText().isBlank()) {
                throw new IllegalArgumentException("Ligne « " + ligne + " » : choisissez une colonne.");
            }
            if (!colonnes.contains(colonne.asText())) {
                throw new IllegalArgumentException("Ligne « " + ligne + " » : la colonne « " + colonne.asText()
                        + " » n'existe pas.");
            }
            resultat.put(ligne, colonne.asText());
        }
        return resultat;
    }

    /** Nombre entre min et max, avec au plus « decimales » chiffres après la virgule. */
    private static BigDecimal nombre(JsonNode valeur, JsonNode config) {
        if (!valeur.isNumber()) {
            throw new IllegalArgumentException("La réponse doit être un nombre.");
        }
        BigDecimal nombre = valeur.decimalValue().stripTrailingZeros();
        int decimales = config.path("decimales").asInt(0);
        if (Math.max(nombre.scale(), 0) > decimales) {
            throw new IllegalArgumentException(decimales == 0 ? "La réponse doit être un nombre entier."
                    : "La réponse ne doit pas avoir plus de " + decimales + " chiffre(s) après la virgule.");
        }
        if (config.hasNonNull("min") && nombre.compareTo(config.get("min").decimalValue()) < 0
                || config.hasNonNull("max") && nombre.compareTo(config.get("max").decimalValue()) > 0) {
            throw new IllegalArgumentException("La valeur doit être comprise entre "
                    + config.path("min").asText("-∞") + " et " + config.path("max").asText("+∞") + ".");
        }
        return nombre;
    }

    private static LocalDate date(JsonNode valeur, JsonNode config) {
        LocalDate date;
        try {
            date = LocalDate.parse(texte(valeur, "une date AAAA-MM-JJ"));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("La date doit être au format AAAA-MM-JJ.");
        }
        if (config.hasNonNull("dateMin") && date.isBefore(LocalDate.parse(config.get("dateMin").asText()))) {
            throw new IllegalArgumentException("La date doit être le " + config.get("dateMin").asText() + " ou après.");
        }
        if (config.hasNonNull("dateMax") && date.isAfter(LocalDate.parse(config.get("dateMax").asText()))) {
            throw new IllegalArgumentException("La date doit être le " + config.get("dateMax").asText() + " ou avant.");
        }
        return date;
    }

    private static String texte(JsonNode valeur, String attendu) {
        if (!valeur.isTextual()) {
            throw new IllegalArgumentException("La réponse doit être " + attendu + ".");
        }
        return valeur.asText();
    }

    /** L'option doit appartenir à cette question. */
    private static OptionQuestion option(Question question, String valeur) {
        Optional<OptionQuestion> option = question.getOptions().stream()
                .filter(o -> o.getValeur().equals(valeur))
                .findFirst();
        return option.orElseThrow(() -> new IllegalArgumentException(
                "L'option « " + valeur + " » n'existe pas pour cette question."));
    }

    private static ReponseQuestion avecOption(Question question, OptionQuestion option) {
        ReponseQuestion ligne = new ReponseQuestion(question);
        ligne.setOption(option);
        return ligne;
    }

    private static ReponseQuestion numerique(Question question, BigDecimal valeur) {
        ReponseQuestion ligne = new ReponseQuestion(question);
        ligne.setValeurNumerique(valeur);
        return ligne;
    }

    private static ReponseQuestion avecJson(Question question, JsonNode json) {
        ReponseQuestion ligne = new ReponseQuestion(question);
        ligne.setValeurJson(json);
        return ligne;
    }
}
