package com.batulo.service;

import com.batulo.enums.TypeQuestion;
import com.batulo.exception.RegleGestionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Valide la configuration JSON et le nombre d'options d'une question selon son type, et renvoie la configuration
 * normalisée (valeurs par défaut ajoutées, textes nettoyés). Toute erreur donne 400 QUESTION_INVALIDE avec un
 * message précis : clé inconnue, valeur du mauvais type, bornes incohérentes...
 */
@Component
public class ValidateurQuestion {

    static final String CODE = "QUESTION_INVALIDE";

    static final int OPTIONS_MIN = 2;
    static final int TEXTE_COURT_MAX = 255;
    static final int TEXTE_LONG_MAX = 5000;
    static final int TEXTE_LONG_DEFAUT = 2000;
    static final int DECIMALES_MAX = 4;
    static final int MATRICE_LIGNES_MAX = 30;
    static final int MATRICE_COLONNES_MIN = 2;
    static final int MATRICE_COLONNES_MAX = 10;
    static final int LIBELLE_CONFIG_MAX = 200;

    private final ObjectMapper objectMapper;

    public ValidateurQuestion(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param configuration telle qu'envoyée (null accepté : configuration par défaut du type)
     * @param nombreOptions nombre d'options envoyées
     * @return configuration normalisée, à enregistrer
     */
    public ObjectNode valider(TypeQuestion type, JsonNode configuration, int nombreOptions) {
        if (type.isAvecOptions() && nombreOptions < OPTIONS_MIN) {
            throw erreur("Une question " + type + " doit avoir au moins " + OPTIONS_MIN + " options.");
        }
        if (!type.isAvecOptions() && nombreOptions > 0) {
            throw erreur("Une question " + type + " n'a pas d'options.");
        }
        if (configuration != null && !configuration.isNull() && !configuration.isObject()) {
            throw erreur("La configuration doit être un objet JSON.");
        }
        ObjectNode config = configuration == null || configuration.isNull()
                ? objectMapper.createObjectNode()
                : ((ObjectNode) configuration).deepCopy();

        return switch (type) {
            case CHOIX_UNIQUE, CHOIX_MULTIPLE, CLASSEMENT, OUI_NON -> sansConfiguration(type, config);
            case TEXTE_COURT -> texte(config, TEXTE_COURT_MAX, TEXTE_COURT_MAX);
            case TEXTE_LONG -> texte(config, TEXTE_LONG_MAX, TEXTE_LONG_DEFAUT);
            case ECHELLE -> echelle(config);
            case NUMERIQUE -> numerique(config);
            case DATE -> date(config);
            case MATRICE -> matrice(config);
        };
    }

    private ObjectNode sansConfiguration(TypeQuestion type, ObjectNode config) {
        if (!config.isEmpty()) {
            throw erreur("Une question " + type + " n'a pas de configuration : envoyez {}.");
        }
        return config;
    }

    /** {longueurMax} de 1 à max. */
    private ObjectNode texte(ObjectNode config, int max, int defaut) {
        clesAutorisees(config, "longueurMax");
        int longueurMax = entier(config, "longueurMax", defaut);
        if (longueurMax < 1 || longueurMax > max) {
            throw erreur("longueurMax doit être compris entre 1 et " + max + ".");
        }
        config.put("longueurMax", longueurMax);
        return config;
    }

    /** {min: 1, max: 5 ou 10, libelleMin, libelleMax}. */
    private ObjectNode echelle(ObjectNode config) {
        clesAutorisees(config, "min", "max", "libelleMin", "libelleMax");
        int min = entier(config, "min", 1);
        int max = entier(config, "max", 5);
        if (min != 1 || (max != 5 && max != 10)) {
            throw erreur("Une ECHELLE va de 1 à 5 ou de 1 à 10 (min: 1, max: 5 ou 10).");
        }
        config.put("min", min);
        config.put("max", max);
        texteFacultatif(config, "libelleMin");
        texteFacultatif(config, "libelleMax");
        return config;
    }

    /** {min, max, decimales} : bornes facultatives mais min ≤ max ; decimales de 0 à 4 (0 par défaut). */
    private ObjectNode numerique(ObjectNode config) {
        clesAutorisees(config, "min", "max", "decimales");
        Double min = nombreFacultatif(config, "min");
        Double max = nombreFacultatif(config, "max");
        if (min != null && max != null && min > max) {
            throw erreur("min doit être inférieur ou égal à max.");
        }
        int decimales = entier(config, "decimales", 0);
        if (decimales < 0 || decimales > DECIMALES_MAX) {
            throw erreur("decimales doit être compris entre 0 et " + DECIMALES_MAX + ".");
        }
        config.put("decimales", decimales);
        return config;
    }

    /** {dateMin, dateMax} au format AAAA-MM-JJ, facultatives mais dateMin ≤ dateMax. */
    private ObjectNode date(ObjectNode config) {
        clesAutorisees(config, "dateMin", "dateMax");
        LocalDate dateMin = dateFacultative(config, "dateMin");
        LocalDate dateMax = dateFacultative(config, "dateMax");
        if (dateMin != null && dateMax != null && dateMin.isAfter(dateMax)) {
            throw erreur("dateMin doit être antérieure ou égale à dateMax.");
        }
        return config;
    }

    /** {lignes: 1 à 30 textes, colonnes: 2 à 10 textes}, non vides et sans doublon. */
    private ObjectNode matrice(ObjectNode config) {
        clesAutorisees(config, "lignes", "colonnes");
        config.set("lignes", listeTextes(config, "lignes", 1, MATRICE_LIGNES_MAX));
        config.set("colonnes", listeTextes(config, "colonnes", MATRICE_COLONNES_MIN, MATRICE_COLONNES_MAX));
        return config;
    }

    private void clesAutorisees(ObjectNode config, String... cles) {
        List<String> autorisees = List.of(cles);
        Iterator<String> noms = config.fieldNames();
        while (noms.hasNext()) {
            String nom = noms.next();
            if (!autorisees.contains(nom)) {
                throw erreur("Clé de configuration inconnue : « " + nom + " » (acceptées : "
                        + String.join(", ", autorisees) + ").");
            }
        }
    }

    private int entier(ObjectNode config, String cle, int defaut) {
        JsonNode valeur = config.get(cle);
        if (valeur == null || valeur.isNull()) {
            return defaut;
        }
        if (!valeur.isIntegralNumber() || !valeur.canConvertToInt()) {
            throw erreur(cle + " doit être un nombre entier.");
        }
        return valeur.intValue();
    }

    private Double nombreFacultatif(ObjectNode config, String cle) {
        JsonNode valeur = config.get(cle);
        if (valeur == null || valeur.isNull()) {
            config.remove(cle);
            return null;
        }
        if (!valeur.isNumber()) {
            throw erreur(cle + " doit être un nombre.");
        }
        return valeur.doubleValue();
    }

    private void texteFacultatif(ObjectNode config, String cle) {
        JsonNode valeur = config.get(cle);
        if (valeur == null || valeur.isNull()) {
            config.remove(cle);
            return;
        }
        if (!valeur.isTextual()) {
            throw erreur(cle + " doit être un texte.");
        }
        String texte = valeur.asText().trim();
        if (texte.isEmpty()) {
            config.remove(cle);
        } else if (texte.length() > LIBELLE_CONFIG_MAX) {
            throw erreur(cle + " ne doit pas dépasser " + LIBELLE_CONFIG_MAX + " caractères.");
        } else {
            config.put(cle, texte);
        }
    }

    private LocalDate dateFacultative(ObjectNode config, String cle) {
        JsonNode valeur = config.get(cle);
        if (valeur == null || valeur.isNull()) {
            config.remove(cle);
            return null;
        }
        if (!valeur.isTextual()) {
            throw erreur(cle + " doit être une date au format AAAA-MM-JJ.");
        }
        try {
            return LocalDate.parse(valeur.asText());
        } catch (DateTimeParseException e) {
            throw erreur(cle + " doit être une date au format AAAA-MM-JJ.");
        }
    }

    private ArrayNode listeTextes(ObjectNode config, String cle, int min, int max) {
        JsonNode valeur = config.get(cle);
        if (valeur == null || !valeur.isArray()) {
            throw erreur("Une MATRICE exige « " + cle + " » : une liste de textes.");
        }
        if (valeur.size() < min || valeur.size() > max) {
            throw erreur("« " + cle + " » doit contenir entre " + min + " et " + max + " éléments.");
        }
        ArrayNode resultat = objectMapper.createArrayNode();
        Set<String> vus = new HashSet<>();
        for (JsonNode element : valeur) {
            if (!element.isTextual() || element.asText().isBlank()) {
                throw erreur("« " + cle + " » ne doit contenir que des textes non vides.");
            }
            String texte = element.asText().trim();
            if (texte.length() > LIBELLE_CONFIG_MAX) {
                throw erreur("Un élément de « " + cle + " » ne doit pas dépasser " + LIBELLE_CONFIG_MAX + " caractères.");
            }
            if (!vus.add(texte.toLowerCase(Locale.ROOT))) {
                throw erreur("« " + cle + " » contient deux fois « " + texte + " ».");
            }
            resultat.add(texte);
        }
        return resultat;
    }

    private static RegleGestionException erreur(String message) {
        return new RegleGestionException(CODE, message);
    }
}
