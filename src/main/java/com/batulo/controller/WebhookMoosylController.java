package com.batulo.controller;

import com.batulo.exception.ErrorResponse;
import com.batulo.service.PaiementMobileService;
import com.batulo.service.PaiementMobileService.ResultatWebhook;
import com.batulo.service.paiement.SignatureWebhookMoosyl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Webhook de Moosyl, sans compte : la signature HMAC-SHA256 du corps brut (x-webhook-signature) remplace
 * l'authentification. Hors de /api/public/** pour ne pas être freiné par la limite par IP lors des réessais.
 */
@RestController
@Tag(name = "Webhooks", description = "Notifications des prestataires de paiement")
public class WebhookMoosylController {

    private final PaiementMobileService paiementMobileService;

    public WebhookMoosylController(PaiementMobileService paiementMobileService) {
        this.paiementMobileService = paiementMobileService;
    }

    @PostMapping("/api/webhooks/moosyl")
    @SecurityRequirements
    @Operation(summary = "Webhook Moosyl (paiement mobile)",
            description = "Événements payment-created et payment-updated : « completed » active ou prolonge "
                    + "l'abonnement (une seule fois par paiement, même si Moosyl réessaie), « failed » et "
                    + "« cancelled » marquent le paiement ECHOUE. 401 SIGNATURE_INVALIDE, 400 WEBHOOK_INVALIDE ; "
                    + "200 sinon, même pour un événement sans effet (Moosyl ne réessaie alors pas).")
    public ResponseEntity<?> recevoir(@RequestBody byte[] corps,
                                      @RequestHeader(value = SignatureWebhookMoosyl.EN_TETE, required = false)
                                      String signature,
                                      @RequestHeader(value = "x-webhook-delivery-id", required = false)
                                      String idLivraison) {
        ResultatWebhook resultat = paiementMobileService.traiterWebhook(corps, signature, idLivraison);
        return switch (resultat) {
            case SIGNATURE_INVALIDE -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("SIGNATURE_INVALIDE", "Signature du webhook absente ou invalide."));
            case CORPS_INVALIDE -> ResponseEntity.badRequest()
                    .body(new ErrorResponse("WEBHOOK_INVALIDE", "Corps du webhook illisible."));
            case TRAITE, IGNORE -> ResponseEntity.ok(Map.of("recu", true, "resultat", resultat.name()));
        };
    }
}
