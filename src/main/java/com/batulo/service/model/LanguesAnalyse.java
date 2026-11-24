package com.batulo.service.model;

import com.batulo.enums.Langue;

import java.util.List;

/**
 * Langues d'une analyse (statistiques, exports, rapports).
 *
 * @param libelles langue des libellés ; null : langue principale du sondage demandé (texte principal en secours)
 * @param reponses filtre sur la langue de réponse (OU entre elles) ; vide : toutes les langues
 */
public record LanguesAnalyse(Langue libelles, List<Langue> reponses) {

    public static final LanguesAnalyse DEFAUT = new LanguesAnalyse(null, List.of());

    public LanguesAnalyse {
        reponses = reponses == null ? List.of() : reponses.stream().distinct().toList();
    }
}
