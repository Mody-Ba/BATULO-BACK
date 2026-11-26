package com.batulo.entity;

import com.batulo.enums.Formule;
import com.batulo.enums.FournisseurPaiement;
import com.batulo.enums.StatutPaiement;
import com.batulo.enums.TypePaiement;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Trace d'un paiement Stripe. Créé EN_ATTENTE avec l'intention de paiement (PaymentIntent), passé à REUSSI
 * une fois le paiement revérifié auprès de Stripe. Aucune donnée de carte n'est stockée chez Batulo :
 * seul l'identifiant du PaymentIntent est conservé.
 */
@Entity
@Table(name = "paiement")
public class Paiement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "abonnement_id", nullable = false)
    private Abonnement abonnement;

    /** PaymentIntent Stripe ; null pour un paiement Moosyl. */
    @Column(name = "stripe_payment_intent_id", unique = true)
    private String stripePaymentIntentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FournisseurPaiement fournisseur = FournisseurPaiement.STRIPE;

    /** Référence Batulo (UUID) envoyée à Moosyl comme transactionId ; null pour Stripe. */
    @Column(unique = true, length = 64)
    private String reference;

    /** Id de la session de paiement Moosyl. */
    @Column(name = "session_externe_id", length = 100)
    private String sessionExterneId;

    /** ABONNEMENT (formule renseignée) ou ACHAT_SONDAGES (quantite renseignée). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypePaiement type = TypePaiement.ABONNEMENT;

    /** Formule payée ; null pour un achat de sondages. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Formule formule;

    /** Nombre de sondages achetés ; null pour un abonnement. */
    @Column
    private Integer quantite;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false, length = 3)
    private String devise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutPaiement statut = StatutPaiement.EN_ATTENTE;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private Instant dateCreation;

    @Column(name = "date_paiement")
    private Instant datePaiement;

    public Paiement() {
    }

    @PrePersist
    void avantCreation() {
        dateCreation = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Abonnement getAbonnement() {
        return abonnement;
    }

    public void setAbonnement(Abonnement abonnement) {
        this.abonnement = abonnement;
    }

    public String getStripePaymentIntentId() {
        return stripePaymentIntentId;
    }

    public void setStripePaymentIntentId(String stripePaymentIntentId) {
        this.stripePaymentIntentId = stripePaymentIntentId;
    }

    public Formule getFormule() {
        return formule;
    }

    public void setFormule(Formule formule) {
        this.formule = formule;
    }

    public BigDecimal getMontant() {
        return montant;
    }

    public void setMontant(BigDecimal montant) {
        this.montant = montant;
    }

    public String getDevise() {
        return devise;
    }

    public void setDevise(String devise) {
        this.devise = devise;
    }

    public StatutPaiement getStatut() {
        return statut;
    }

    public void setStatut(StatutPaiement statut) {
        this.statut = statut;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }

    public Instant getDatePaiement() {
        return datePaiement;
    }

    public void setDatePaiement(Instant datePaiement) {
        this.datePaiement = datePaiement;
    }

    public TypePaiement getType() {
        return type;
    }

    public void setType(TypePaiement type) {
        this.type = type;
    }

    public Integer getQuantite() {
        return quantite;
    }

    public void setQuantite(Integer quantite) {
        this.quantite = quantite;
    }

    public FournisseurPaiement getFournisseur() {
        return fournisseur;
    }

    public void setFournisseur(FournisseurPaiement fournisseur) {
        this.fournisseur = fournisseur;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getSessionExterneId() {
        return sessionExterneId;
    }

    public void setSessionExterneId(String sessionExterneId) {
        this.sessionExterneId = sessionExterneId;
    }
}
