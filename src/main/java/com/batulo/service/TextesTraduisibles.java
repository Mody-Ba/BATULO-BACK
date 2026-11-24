package com.batulo.service;

import com.batulo.entity.OptionQuestion;
import com.batulo.entity.Question;
import com.batulo.entity.Section;
import com.batulo.entity.Sondage;
import com.batulo.enums.Langue;
import com.batulo.enums.TypeQuestion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Inventaire des textes traduisibles d'un sondage, dans l'ordre du formulaire : seuls les textes non vides de la
 * langue principale comptent (le texte du consentement seulement s'il est exigé). Chaque texte a une clé d'API
 * stable tant que l'élément existe (ids, rang de ligne/colonne de matrice) ; la traduction est rangée dans le JSON
 * « traductions » de l'élément (lignes et colonnes de matrice : sous leur texte principal, leur valeur stable).
 */
final class TextesTraduisibles {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private TextesTraduisibles() {
    }

    /** Élément porteur du JSON des traductions. */
    record Porteur(Supplier<JsonNode> lire, Consumer<JsonNode> ecrire) {
    }

    /**
     * @param element  SONDAGE, SECTION, QUESTION, OPTION, LIGNE ou COLONNE
     * @param champ    titre, description, objetEnquete, texteConfidentialite, texteConsentement, libelle, aide,
     *                 libelleMin, libelleMax, lignes ou colonnes
     * @param sousCle  texte principal d'une ligne ou d'une colonne de matrice ; null sinon
     */
    record Texte(String cle, String element, String champ, Long sectionId, Long questionId, Long optionId,
                 String source, int longueurMax, Porteur porteur, String sousCle) {

        /** Traduction enregistrée (non vide), ou null. */
        String traduction(Langue langue) {
            JsonNode tout = porteur.lire().get();
            if (tout == null) {
                return null;
            }
            JsonNode noeud = tout.path(langue.getCode()).get(champ);
            if (noeud != null && sousCle != null) {
                noeud = noeud.get(sousCle);
            }
            return noeud != null && noeud.isTextual() && !noeud.asText().isBlank() ? noeud.asText() : null;
        }

        /** Enregistre (ou efface, si null) la traduction ; le JSON de l'élément est remplacé par une copie. */
        void ecrire(Langue langue, String valeur) {
            JsonNode actuel = porteur.lire().get();
            ObjectNode tout = actuel != null && actuel.isObject() ? ((ObjectNode) actuel).deepCopy() : JSON.objectNode();
            ObjectNode parLangue = tout.has(langue.getCode()) && tout.get(langue.getCode()).isObject()
                    ? (ObjectNode) tout.get(langue.getCode()) : tout.putObject(langue.getCode());
            if (sousCle == null) {
                if (valeur == null) {
                    parLangue.remove(champ);
                } else {
                    parLangue.put(champ, valeur);
                }
            } else {
                ObjectNode table = parLangue.has(champ) && parLangue.get(champ).isObject()
                        ? (ObjectNode) parLangue.get(champ) : parLangue.putObject(champ);
                if (valeur == null) {
                    table.remove(sousCle);
                } else {
                    table.put(sousCle, valeur);
                }
                if (table.isEmpty()) {
                    parLangue.remove(champ);
                }
            }
            if (parLangue.isEmpty()) {
                tout.remove(langue.getCode());
            }
            porteur.ecrire().accept(tout.isEmpty() ? null : tout);
        }
    }

    /** Retire la traduction d'un champ du sondage (titre, description...) dans toutes les langues. */
    static void effacer(Sondage sondage, String champ) {
        sondage.setTraductions(sansChamp(sondage.getTraductions(), champ));
    }

    /**
     * Un texte principal modifié perd ses traductions dans toutes les langues (elles traduisaient l'ancien texte) :
     * la complétude le signale de nouveau comme manquant.
     *
     * @return le JSON à enregistrer (inchangé si le texte n'a pas changé)
     */
    static JsonNode apresModification(JsonNode traductions, String champ, String ancien, String nouveau) {
        return Objects.equals(ancien, nouveau) ? traductions : sansChamp(traductions, champ);
    }

    private static JsonNode sansChamp(JsonNode traductions, String champ) {
        if (traductions == null || !traductions.isObject()) {
            return traductions;
        }
        ObjectNode tout = traductions.deepCopy();
        List<String> vides = new ArrayList<>();
        tout.fields().forEachRemaining(e -> {
            if (e.getValue() instanceof ObjectNode parLangue) {
                parLangue.remove(champ);
                if (parLangue.isEmpty()) {
                    vides.add(e.getKey());
                }
            }
        });
        tout.remove(vides);
        return tout.isEmpty() ? null : tout;
    }

    static List<Texte> inventaire(Sondage sondage) {
        List<Texte> textes = new ArrayList<>();
        Porteur ps = new Porteur(sondage::getTraductions, sondage::setTraductions);
        ajouter(textes, "sondage.titre", "SONDAGE", "titre", null, null, null, sondage.getTitre(), 200, ps, null);
        ajouter(textes, "sondage.description", "SONDAGE", "description", null, null, null,
                sondage.getDescription(), 5000, ps, null);
        ajouter(textes, "sondage.objetEnquete", "SONDAGE", "objetEnquete", null, null, null,
                sondage.getObjetEnquete(), 5000, ps, null);
        ajouter(textes, "sondage.texteConfidentialite", "SONDAGE", "texteConfidentialite", null, null, null,
                sondage.getTexteConfidentialite(), 10000, ps, null);
        if (sondage.isConsentementRequis()) {
            ajouter(textes, "sondage.texteConsentement", "SONDAGE", "texteConsentement", null, null, null,
                    sondage.getTexteConsentement(), 2000, ps, null);
        }
        for (Section section : sondage.getSections()) {
            Porteur psec = new Porteur(section::getTraductions, section::setTraductions);
            String base = "section." + section.getId();
            ajouter(textes, base + ".titre", "SECTION", "titre", section.getId(), null, null, section.getTitre(),
                    200, psec, null);
            ajouter(textes, base + ".description", "SECTION", "description", section.getId(), null, null,
                    section.getDescription(), 5000, psec, null);
            for (Question question : section.getQuestions()) {
                questions(textes, section, question);
            }
        }
        return textes;
    }

    private static void questions(List<Texte> textes, Section section, Question question) {
        Porteur pq = new Porteur(question::getTraductions, question::setTraductions);
        String base = "question." + question.getId();
        Long s = section.getId();
        Long q = question.getId();
        ajouter(textes, base + ".libelle", "QUESTION", "libelle", s, q, null, question.getLibelle(), 500, pq, null);
        ajouter(textes, base + ".aide", "QUESTION", "aide", s, q, null, question.getAide(), 500, pq, null);
        JsonNode config = question.getConfiguration();
        if (question.getType() == TypeQuestion.ECHELLE) {
            for (String champ : List.of("libelleMin", "libelleMax")) {
                JsonNode texte = config.get(champ);
                ajouter(textes, base + "." + champ, "QUESTION", champ, s, q, null,
                        texte == null || texte.isNull() ? null : texte.asText(), ValidateurQuestion.LIBELLE_CONFIG_MAX,
                        pq, null);
            }
        }
        if (question.getType() == TypeQuestion.MATRICE) {
            int rang = 0;
            for (JsonNode ligne : config.path("lignes")) {
                ajouter(textes, base + ".ligne." + (++rang), "LIGNE", "lignes", s, q, null, ligne.asText(),
                        ValidateurQuestion.LIBELLE_CONFIG_MAX, pq, ligne.asText());
            }
            rang = 0;
            for (JsonNode colonne : config.path("colonnes")) {
                ajouter(textes, base + ".colonne." + (++rang), "COLONNE", "colonnes", s, q, null, colonne.asText(),
                        ValidateurQuestion.LIBELLE_CONFIG_MAX, pq, colonne.asText());
            }
        }
        for (OptionQuestion option : question.getOptions()) {
            ajouter(textes, "option." + option.getId() + ".libelle", "OPTION", "libelle", s, q, option.getId(),
                    option.getLibelle(), 200, new Porteur(option::getTraductions, option::setTraductions), null);
        }
    }

    private static void ajouter(List<Texte> textes, String cle, String element, String champ, Long sectionId,
                                Long questionId, Long optionId, String source, int max, Porteur porteur,
                                String sousCle) {
        if (source != null && !source.isBlank()) {
            textes.add(new Texte(cle, element, champ, sectionId, questionId, optionId, source, max, porteur, sousCle));
        }
    }
}
