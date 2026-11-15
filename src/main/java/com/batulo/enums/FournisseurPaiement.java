package com.batulo.enums;

/** Prestataire qui a encaissé un paiement d'abonnement. */
public enum FournisseurPaiement {
    /** Carte bancaire (Stripe). */
    STRIPE,
    /** Paiement mobile mauritanien (Moosyl : Bankily, Sedad, Masrivi...), en MRU. */
    MOOSYL
}
