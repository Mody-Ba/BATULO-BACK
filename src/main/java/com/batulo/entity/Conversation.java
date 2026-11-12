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

/** Conversation entre deux côtés (AD-14, GE-07, SA-07), toujours ancrée sur un admin : avec l'un de ses gestionnaires, ou (gestionnaire null) avec Batulo, dont les super admins partagent la boîte. */
@Entity
@Table(name = "conversation")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private Utilisateur admin;

    /** Null : conversation avec Batulo (super admins). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gestionnaire_id")
    private Utilisateur gestionnaire;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private Instant dateCreation;

    /** Tri des conversations : la plus active d'abord. */
    @Column(name = "dernier_message_le", nullable = false)
    private Instant dernierMessageLe;

    public Conversation() {
    }

    @PrePersist
    void avantCreation() {
        dateCreation = Instant.now();
        if (dernierMessageLe == null) {
            dernierMessageLe = dateCreation;
        }
    }

    public Long getId() {
        return id;
    }

    public Utilisateur getAdmin() {
        return admin;
    }

    public void setAdmin(Utilisateur admin) {
        this.admin = admin;
    }

    public Utilisateur getGestionnaire() {
        return gestionnaire;
    }

    public void setGestionnaire(Utilisateur gestionnaire) {
        this.gestionnaire = gestionnaire;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }

    public Instant getDernierMessageLe() {
        return dernierMessageLe;
    }

    public void setDernierMessageLe(Instant dernierMessageLe) {
        this.dernierMessageLe = dernierMessageLe;
    }
}
