package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Création ou modification d'un sondage (titre et description). */
public record SondageRequest(
        @NotBlank(message = "le titre est obligatoire")
        @Size(max = 200, message = "le titre ne doit pas dépasser 200 caractères")
        @Schema(example = "Enquête agricole – campagne 2026")
        String titre,

        @Size(max = 5000, message = "la description ne doit pas dépasser 5000 caractères")
        @Schema(example = "Suivi des exploitations du bassin arachidier")
        String description,

        @Schema(description = "true (défaut) : une seule réponse par appareil (sondage en ligne) ; false : collecte "
                + "terrain, plusieurs réponses depuis le même appareil. Modifiable dans le brouillon, figé à la publication.",
                example = "true")
        Boolean reponseUnique,

        // Page d'accueil : absent (null) = inchangé ; "" efface le texte.
        @Size(max = 5000, message = "l'objet de l'enquête ne doit pas dépasser 5000 caractères")
        @Schema(description = "Page d'accueil : objet de l'enquête. Absent : inchangé ; \"\" l'efface.",
                example = "Mieux connaître les besoins des producteurs de la région.")
        String objetEnquete,

        @Size(max = 10000, message = "le texte de confidentialité ne doit pas dépasser 10000 caractères")
        @Schema(description = "Page d'accueil : confidentialité des réponses. Absent : inchangé ; \"\" l'efface.",
                example = "Vos réponses sont anonymes et ne servent qu'à cette étude.")
        String texteConfidentialite,

        @Schema(description = "true : le répondant doit cocher la case de consentement. Absent : inchangé.",
                example = "true")
        Boolean consentementRequis,

        @Size(max = 2000, message = "le texte de consentement ne doit pas dépasser 2000 caractères")
        @Schema(description = "Texte de la case, obligatoire si consentementRequis (400 TEXTE_CONSENTEMENT_REQUIS). "
                + "Absent : inchangé ; \"\" l'efface.", example = "J'accepte de participer à cette enquête.")
        String texteConsentement
) {
}
