package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Achat de sondages par paiement mobile (Moosyl : Bankily, Sedad, Masrivi...), en MRU. */
public record AchatSondagesMobileRequest(
        @NotNull(message = "la quantité est obligatoire (1 à 1000)")
        @Schema(description = "Nombre de sondages, de 1 à 1000 (400 QUANTITE_INVALIDE sinon)", example = "5",
                minimum = "1", maximum = "1000")
        Integer quantite,
        @Pattern(regexp = "\\d{8}", message = "le téléphone doit compter 8 chiffres")
        @Schema(description = "Facultatif : numéro à 8 chiffres du payeur ; sinon Moosyl le demande", example = "22222222")
        String telephone
) {
}
