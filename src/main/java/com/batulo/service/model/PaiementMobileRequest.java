package com.batulo.service.model;

import com.batulo.enums.Formule;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Paiement mobile (Moosyl : Bankily, Sedad, Masrivi...) d'une formule, en MRU. */
public record PaiementMobileRequest(
        @NotNull(message = "la formule est obligatoire (MENSUEL ou ANNUEL)")
        @Schema(example = "MENSUEL")
        Formule formule,

        @Pattern(regexp = "\\d{8}", message = "le téléphone doit compter 8 chiffres")
        @Schema(description = "Facultatif : numéro à 8 chiffres du payeur ; sinon Moosyl le demande", example = "22222222")
        String telephone
) {
}
