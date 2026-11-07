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
 * Lien « mot de passe oublié » : valable 1 h et utilisable une seule fois (RG-07).
 * Seul le hash SHA-256 du jeton est stocké, jamais le jeton envoyé par courriel.
 */
@Entity
@Table(name = "jeton_reinitialisation")
public class JetonReinitialisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(name = "jeton_hash", nullable = false, unique = true, length = 64)
    private String jetonHash;

    @Column(name = "expire_le", nullable = false)
    private Instant expireLe;

    /** Null tant que le lien n'a pas servi. */
    @Column(name = "utilise_le")
    private Instant utiliseLe;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private Instant dateCreation;

    public JetonReinitialisation() {
    }

    @PrePersist
    void avantCreation() {
        dateCreation = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Utilisateur getUtilisateur() {
        return utilisateur;
    }

    public void setUtilisateur(Utilisateur utilisateur) {
        this.utilisateur = utilisateur;
    }

    public String getJetonHash() {
        return jetonHash;
    }

    public void setJetonHash(String jetonHash) {
        this.jetonHash = jetonHash;
    }

    public Instant getExpireLe() {
        return expireLe;
    }

    public void setExpireLe(Instant expireLe) {
        this.expireLe = expireLe;
    }

    public Instant getUtiliseLe() {
        return utiliseLe;
    }

    public void setUtiliseLe(Instant utiliseLe) {
        this.utiliseLe = utiliseLe;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }
}
