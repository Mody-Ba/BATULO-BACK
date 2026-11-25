package com.batulo.service.model;

import com.batulo.enums.Langue;
import com.batulo.enums.StatutSondage;

import java.time.Instant;
import java.util.List;

/** Sondage complet : sections > questions > options, triées par ordre. */
public record SondageModel(
        Long id,
        Long entrepriseId,
        String titre,
        String description,
        /** Calculé à partir des dates : NON_COMMENCE, EN_COURS ou TERMINE. */
        StatutSondage statut,
        /** false : brouillon (seul état modifiable) ; true : publié (contenu gelé). */
        boolean publie,
        Instant datePublication,
        Instant dateFermeture,
        int version,
        /** Sondage dont celui-ci est issu (source réutilisée ou dupliquée, version précédente) ; null si créé vide. */
        Long sondageOrigineId,
        boolean estModele,
        /** true : une seule réponse par appareil ; false : collecte terrain. */
        boolean reponseUnique,
        /** Page d'accueil : objet de l'enquête ; null si aucun. */
        String objetEnquete,
        /** Page d'accueil : texte sur la confidentialité des réponses ; null si aucun. */
        String texteConfidentialite,
        /** true : le répondant doit cocher la case de consentement pour envoyer ses réponses. */
        boolean consentementRequis,
        /** Texte de la case de consentement (renseigné si consentementRequis). */
        String texteConsentement,
        /** Langue dans laquelle le contenu est saisi (fr par défaut). */
        Langue languePrincipale,
        /** Langues supplémentaires (sans la principale), traduites dans l'éditeur de traductions. */
        List<Langue> languesSupplementaires,
        /** true : réponses reçues par invitation associées aux invités ; false : anonymes (défaut). */
        boolean reponsesNominatives,
        AuteurModel creePar,
        Instant dateCreation,
        Instant dateModification,
        List<SectionModel> sections
) {
}
