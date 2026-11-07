package com.batulo.service;

import com.batulo.exception.ResourceNotFoundException;
import com.batulo.repository.PaiementRepository;
import com.batulo.service.model.ConfirmationPaiementModel;
import com.batulo.service.model.IntentionStripeModel;
import com.batulo.service.paiement.PaiementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** N'existe qu'avec le profil dev : en production, ni ce service ni sa route ne sont créés. */
@Service
@Profile("dev")
@Transactional(readOnly = true)
public class DevPaiementServiceImpl implements DevPaiementService {

    private static final Logger log = LoggerFactory.getLogger(DevPaiementServiceImpl.class);

    private final PaiementRepository paiementRepository;
    private final PaiementService paiementService;

    public DevPaiementServiceImpl(PaiementRepository paiementRepository, PaiementService paiementService) {
        this.paiementRepository = paiementRepository;
        this.paiementService = paiementService;
    }

    @Override
    public IntentionStripeModel payerAvecCarteTest(Long entrepriseId, ConfirmationPaiementModel paiement) {
        String paymentIntentId = paiement.paymentIntentId();
        boolean appartientAEntreprise = paiementRepository.findByStripePaymentIntentId(paymentIntentId)
                .map(p -> p.getAbonnement().getEntreprise().getId().equals(entrepriseId))
                .orElse(false);
        if (!appartientAEntreprise) {
            throw new ResourceNotFoundException("PAIEMENT_INTROUVABLE",
                    "Aucun paiement de votre entreprise ne correspond à ce paymentIntentId.");
        }
        IntentionStripeModel intention = paiementService.payerAvecCarteTest(paymentIntentId);
        log.info("[DEV] PaymentIntent {} payé avec la carte de test : statut Stripe {}", paymentIntentId, intention.statut());
        return intention;
    }
}
