package com.batulo.entity;

import com.batulo.enums.Formule;
import com.batulo.enums.StatutAbonnement;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Abonnement d'une entreprise (un seul par entreprise). Créé EN_ATTENTE_PAIEMENT à l'inscription ;
 * seul un paiement en ligne le rend ACTIF (RG-09).
 */
@Entity
@Table(name = "abonnement")
public class Abonnement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entreprise_id", nullable = false, unique = true)
    private Entreprise entreprise;

    /** Null tant qu'aucune formule n'a été payée. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Formule formule;

    /** Dernier statut enregistré ; le statut réel se recalcule à partir des dates. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatutAbonnement statut = StatutAbonnement.EN_ATTENTE_PAIEMENT;

    @Column(name = "date_debut")
    private Instant dateDebut;

    @Column(name = "date_expiration")
    private Instant dateExpiration;

    @Column(precision = 10, scale = 2)
    private BigDecimal montant;

    @Column(length = 3)
    private String devise;

    /** Le rappel « expire dans 7 jours » a déjà été envoyé pour la période en cours. */
    @Column(name = "notif_7j_envoyee", nullable = false)
    private boolean notif7jEnvoyee;

    /** L'avis « abonnement expiré » a déjà été envoyé pour la période en cours. */
    @Column(name = "notif_expiration_envoyee", nullable = false)
    private boolean notifExpirationEnvoyee;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private Instant dateCreation;

    @Column(name = "date_modification", nullable = false)
    private Instant dateModification;

    public Abonnement() {
    }

    @PrePersist
    void avantCreation() {
        dateCreation = Instant.now();
        dateModification = dateCreation;
    }

    @PreUpdate
    void avantModification() {
        dateModification = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Entreprise getEntreprise() {
        return entreprise;
    }

    public void setEntreprise(Entreprise entreprise) {
        this.entreprise = entreprise;
    }

    public Formule getFormule() {
        return formule;
    }

    public void setFormule(Formule formule) {
        this.formule = formule;
    }

    public StatutAbonnement getStatut() {
        return statut;
    }

    public void setStatut(StatutAbonnement statut) {
        this.statut = statut;
    }

    public Instant getDateDebut() {
        return dateDebut;
    }

    public void setDateDebut(Instant dateDebut) {
        this.dateDebut = dateDebut;
    }

    public Instant getDateExpiration() {
        return dateExpiration;
    }

    public void setDateExpiration(Instant dateExpiration) {
        this.dateExpiration = dateExpiration;
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

    public boolean isNotif7jEnvoyee() {
        return notif7jEnvoyee;
    }

    public void setNotif7jEnvoyee(boolean notif7jEnvoyee) {
        this.notif7jEnvoyee = notif7jEnvoyee;
    }

    public boolean isNotifExpirationEnvoyee() {
        return notifExpirationEnvoyee;
    }

    public void setNotifExpirationEnvoyee(boolean notifExpirationEnvoyee) {
        this.notifExpirationEnvoyee = notifExpirationEnvoyee;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }

    public Instant getDateModification() {
        return dateModification;
    }
}
