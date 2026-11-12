package com.batulo.service.model;

import com.batulo.enums.Formule;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Demande d'augmentation d'abonnement au super admin (AD-05). */
public record DemandeAbonnementRequest(
        @NotNull(message = "la formule souhaitée est obligatoire (MENSUEL ou ANNUEL)")
        @Schema(example = "ANNUEL")
        Formule formuleSouhaitee,

        @NotBlank(message = "le message est obligatoire")
        @Size(max = 2000, message = "le message ne doit pas dépasser 2000 caractères")
        @Schema(example = "Nous lançons trois nouvelles enquêtes : pouvons-nous passer à l'annuel ?")
        String message
) {
}
