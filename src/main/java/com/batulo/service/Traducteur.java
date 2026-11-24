package com.batulo.service;

import com.batulo.entity.OptionQuestion;
import com.batulo.entity.Question;
import com.batulo.entity.Section;
import com.batulo.entity.Sondage;
import com.batulo.enums.Langue;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Seul résolveur des textes d'un sondage dans une langue (classe pure, sans état partagé) : la traduction si elle
 * existe et n'est pas vide, sinon le texte de la langue principale (secours). Les valeurs stables (clés des questions,
 * valeurs des options, textes principaux des lignes et colonnes de matrice) ne passent jamais par ici.
 */
public final class Traducteur {

    private static final Traducteur PRINCIPALE = new Traducteur(null, null);

    /** Langue servie ; null pour le traducteur « langue principale » sans sondage. */
    private final Langue langue;
    /** Code cherché dans les traductions ; null : la langue principale (aucune traduction lue). */
    private final String code;

    private Traducteur(Langue langue, String code) {
        this.langue = langue;
        this.code = code;
    }

    /** Textes de la langue principale, tels qu'ils sont saisis. */
    public static Traducteur principale() {
        return PRINCIPALE;
    }

    /**
     * La langue demandée si c'est l'une des langues supplémentaires de cette version, sinon la langue principale.
     * Changer la langue principale ne change que l'étiquette : une traduction rangée sous la nouvelle langue
     * principale n'est jamais lue.
     */
    public static Traducteur pour(Sondage version, Langue demandee) {
        if (demandee != null && demandee != version.getLanguePrincipale()
                && version.getLanguesSupplementaires().contains(demandee)) {
            return new Traducteur(demandee, demandee.getCode());
        }
        return new Traducteur(version.getLanguePrincipale(), null);
    }

    /** Langue réellement servie (null pour {@link #principale()}). */
    public Langue langue() {
        return langue;
    }

    public boolean estPrincipale() {
        return code == null;
    }

    public String titre(Sondage s) {
        return texte(s.getTraductions(), "titre", s.getTitre());
    }

    public String description(Sondage s) {
        return texte(s.getTraductions(), "description", s.getDescription());
    }

    public String objetEnquete(Sondage s) {
        return texte(s.getTraductions(), "objetEnquete", s.getObjetEnquete());
    }

    public String texteConfidentialite(Sondage s) {
        return texte(s.getTraductions(), "texteConfidentialite", s.getTexteConfidentialite());
    }

    public String texteConsentement(Sondage s) {
        return texte(s.getTraductions(), "texteConsentement", s.getTexteConsentement());
    }

    public String titre(Section s) {
        return texte(s.getTraductions(), "titre", s.getTitre());
    }

    public String description(Section s) {
        return texte(s.getTraductions(), "description", s.getDescription());
    }

    public String libelle(Question q) {
        return texte(q.getTraductions(), "libelle", q.getLibelle());
    }

    public String aide(Question q) {
        return texte(q.getTraductions(), "aide", q.getAide());
    }

    /** libelleMin ou libelleMax d'une ECHELLE (texte de la configuration). */
    public String libelleEchelle(Question q, String champ) {
        JsonNode source = q.getConfiguration().get(champ);
        return texte(q.getTraductions(), champ, source == null || source.isNull() ? null : source.asText());
    }

    /** Libellé affiché d'une ligne (champ « lignes ») ou d'une colonne (« colonnes ») de matrice. */
    public String matrice(Question q, String champ, String textePrincipal) {
        if (code == null || textePrincipal == null) {
            return textePrincipal;
        }
        JsonNode traduction = q.getTraductions() == null ? null
                : q.getTraductions().path(code).path(champ).get(textePrincipal);
        return nonVide(traduction) ? traduction.asText() : textePrincipal;
    }

    public String libelle(OptionQuestion o) {
        return texte(o.getTraductions(), "libelle", o.getLibelle());
    }

    private String texte(JsonNode traductions, String champ, String source) {
        if (code == null || source == null || traductions == null) {
            return source;
        }
        JsonNode traduction = traductions.path(code).get(champ);
        return nonVide(traduction) ? traduction.asText() : source;
    }

    private static boolean nonVide(JsonNode noeud) {
        return noeud != null && noeud.isTextual() && !noeud.asText().isBlank();
    }
}
