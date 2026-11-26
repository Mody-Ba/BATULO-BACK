package com.batulo.service;

import com.batulo.exception.RegleGestionException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Prix de l'achat de sondages à l'unité : montant = quantité × prix unitaire, toujours calculé ici (jamais reçu du
 * frontend). Prix lus dans PRIX_SONDAGE_CAD (carte, 2 décimales au plus) et PRIX_SONDAGE_MRU (Moosyl, MRU entiers) ;
 * l'application refuse de démarrer s'ils manquent ou sont invalides.
 */
@Component
public class TarifAchatSondages {

    public static final int QUANTITE_MIN = 1;
    public static final int QUANTITE_MAX = 1000;

    private final String prixCarteTexte;
    private final String prixMobileTexte;
    private BigDecimal prixCarte;
    private long prixMobile;

    public TarifAchatSondages(@Value("${batulo.achat-sondages.prix-cad:}") String prixCarteTexte,
                              @Value("${batulo.achat-sondages.prix-mru:}") String prixMobileTexte) {
        this.prixCarteTexte = prixCarteTexte;
        this.prixMobileTexte = prixMobileTexte;
    }

    @PostConstruct
    void init() {
        try {
            prixCarte = new BigDecimal(prixCarteTexte.trim());
        } catch (NumberFormatException | NullPointerException e) {
            prixCarte = null;
        }
        if (prixCarte == null || prixCarte.signum() <= 0 || prixCarte.stripTrailingZeros().scale() > 2) {
            throw new IllegalStateException("PRIX_SONDAGE_CAD est absent ou invalide : il faut le prix d'un sondage "
                    + "payé par carte, positif, avec 2 décimales au plus (ex. 0.50). Ajoutez-le dans .env.properties "
                    + "(voir .env.properties.example).");
        }
        prixCarte = prixCarte.setScale(2, RoundingMode.UNNECESSARY);
        try {
            prixMobile = Long.parseLong(prixMobileTexte.trim());
        } catch (NumberFormatException | NullPointerException e) {
            prixMobile = 0;
        }
        if (prixMobile <= 0) {
            throw new IllegalStateException("PRIX_SONDAGE_MRU est absent ou invalide : il faut le prix d'un sondage "
                    + "payé par mobile (Moosyl), en MRU entiers et positif (ex. 20). Ajoutez-le dans .env.properties "
                    + "(voir .env.properties.example).");
        }
    }

    /** 400 QUANTITE_INVALIDE hors de 1 à 1000. */
    public int verifierQuantite(Integer quantite) {
        if (quantite == null || quantite < QUANTITE_MIN || quantite > QUANTITE_MAX) {
            throw new RegleGestionException("QUANTITE_INVALIDE", "Le nombre de sondages doit être compris entre "
                    + QUANTITE_MIN + " et " + QUANTITE_MAX + ".");
        }
        return quantite;
    }

    /** Prix d'un sondage par carte (devise Stripe). */
    public BigDecimal prixCarte() {
        return prixCarte;
    }

    /** Prix d'un sondage par mobile, en MRU entiers. */
    public long prixMobile() {
        return prixMobile;
    }

    public long centimesCarte(int quantite) {
        return prixCarte.movePointRight(2).longValueExact() * quantite;
    }

    public long montantMobile(int quantite) {
        return prixMobile * quantite;
    }
}
