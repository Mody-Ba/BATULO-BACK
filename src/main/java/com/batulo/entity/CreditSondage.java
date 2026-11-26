package com.batulo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Solde de sondages achetés par une entreprise, sans date limite (les achats s'additionnent). Une ligne par
 * entreprise, créée au premier achat. Le solde ne change que par des mises à jour SQL atomiques
 * (CreditSondageRepository.crediter / debiter), jamais en modifiant cette entité.
 */
@Entity
@Table(name = "credit_sondage")
public class CreditSondage {

    @Id
    @Column(name = "entreprise_id")
    private Long entrepriseId;

    @Column(nullable = false)
    private int solde;

    @Column(name = "total_achete", nullable = false)
    private int totalAchete;

    @Column(name = "total_utilise", nullable = false)
    private int totalUtilise;

    @Column(name = "date_modification", nullable = false)
    private Instant dateModification;

    public CreditSondage() {
    }

    public CreditSondage(Long entrepriseId, int solde, int totalAchete) {
        this.entrepriseId = entrepriseId;
        this.solde = solde;
        this.totalAchete = totalAchete;
    }

    @PrePersist
    @PreUpdate
    void avantEcriture() {
        dateModification = Instant.now();
    }

    public Long getEntrepriseId() {
        return entrepriseId;
    }

    public int getSolde() {
        return solde;
    }

    public int getTotalAchete() {
        return totalAchete;
    }

    public int getTotalUtilise() {
        return totalUtilise;
    }

    public Instant getDateModification() {
        return dateModification;
    }
}
