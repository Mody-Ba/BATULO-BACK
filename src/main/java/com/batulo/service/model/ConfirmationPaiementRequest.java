package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Identifiant du PaymentIntent Stripe à vérifier. */
public record ConfirmationPaiementRequest(
        @NotBlank(message = "le paymentIntentId est obligatoire")
        @Size(max = 255, message = "le paymentIntentId ne doit pas dépasser 255 caractères")
        @Pattern(regexp = "^pi_[A-Za-z0-9]+$",
                message = "doit être l'identifiant du PaymentIntent (pi_...), pas le clientSecret")
        @Schema(example = "pi_3Q...")
        String paymentIntentId
) {
}
