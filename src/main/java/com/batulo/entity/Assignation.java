package com.batulo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Assignation d'un sondage à un gestionnaire (AD-10, RG-03). Porte sur la lignée (version 1) : toutes les versions
 * du sondage sont assignées au même gestionnaire.
 */
@Entity
@Table(name = "assignation")
public class Assignation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Version 1 de la lignée. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sondage_id", nullable = false)
    private Sondage sondage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gestionnaire_id", nullable = false)
    private Utilisateur gestionnaire;

    /** Admin qui a fait l'assignation ; null si son compte a été supprimé. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigne_par_id")
    private Utilisateur assignePar;

    @Column(name = "date_assignation", nullable = false, updatable = false)
    private Instant dateAssignation;

    public Assignation() {
    }

    @PrePersist
    void avantCreation() {
        dateAssignation = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Sondage getSondage() {
        return sondage;
    }

    public void setSondage(Sondage sondage) {
        this.sondage = sondage;
    }

    public Utilisateur getGestionnaire() {
        return gestionnaire;
    }

    public void setGestionnaire(Utilisateur gestionnaire) {
        this.gestionnaire = gestionnaire;
    }

    public Utilisateur getAssignePar() {
        return assignePar;
    }

    public void setAssignePar(Utilisateur assignePar) {
        this.assignePar = assignePar;
    }

    public Instant getDateAssignation() {
        return dateAssignation;
    }
}
