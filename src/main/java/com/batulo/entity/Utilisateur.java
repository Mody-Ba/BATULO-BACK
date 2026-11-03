package com.batulo.entity;

import com.batulo.enums.Role;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Compte Batulo (super admin, admin ou gestionnaire). Pas de toString : le hash du mot de passe
 * ne doit jamais se retrouver dans un log par accident.
 */
@Entity
@Table(name = "utilisateur")
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String prenom;

    @Column(nullable = false, length = 100)
    private String nom;

    /** Toujours stocké en minuscules. */
    @Column(nullable = false, unique = true)
    private String courriel;

    /** Hash BCrypt, jamais le mot de passe en clair. */
    @Column(name = "mot_de_passe", nullable = false, length = 100)
    private String motDePasse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** Null pour le super admin. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entreprise_id")
    private Entreprise entreprise;

    @Column(nullable = false)
    private boolean actif = true;

    /** true quand le mot de passe a été généré par le serveur ; il doit être changé à la première connexion. */
    @Column(name = "mot_de_passe_temporaire", nullable = false)
    private boolean motDePasseTemporaire;

    /** Fin de validité du mot de passe temporaire (24 h, RG-06). */
    @Column(name = "mot_de_passe_temporaire_expire_le")
    private Instant motDePasseTemporaireExpireLe;

    /** Permission de créer des sondages (gestionnaires seulement, AD-09). */
    @Column(name = "peut_creer_sondages", nullable = false)
    private boolean peutCreerSondages;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private Instant dateCreation;

    @Column(name = "date_modification", nullable = false)
    private Instant dateModification;

    public Utilisateur() {
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

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getCourriel() {
        return courriel;
    }

    public void setCourriel(String courriel) {
        this.courriel = courriel;
    }

    public String getMotDePasse() {
        return motDePasse;
    }

    public void setMotDePasse(String motDePasse) {
        this.motDePasse = motDePasse;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Entreprise getEntreprise() {
        return entreprise;
    }

    public void setEntreprise(Entreprise entreprise) {
        this.entreprise = entreprise;
    }

    public boolean isActif() {
        return actif;
    }

    public void setActif(boolean actif) {
        this.actif = actif;
    }

    public boolean isMotDePasseTemporaire() {
        return motDePasseTemporaire;
    }

    public void setMotDePasseTemporaire(boolean motDePasseTemporaire) {
        this.motDePasseTemporaire = motDePasseTemporaire;
    }

    public Instant getMotDePasseTemporaireExpireLe() {
        return motDePasseTemporaireExpireLe;
    }

    public void setMotDePasseTemporaireExpireLe(Instant motDePasseTemporaireExpireLe) {
        this.motDePasseTemporaireExpireLe = motDePasseTemporaireExpireLe;
    }

    public boolean isPeutCreerSondages() {
        return peutCreerSondages;
    }

    public void setPeutCreerSondages(boolean peutCreerSondages) {
        this.peutCreerSondages = peutCreerSondages;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }

    public Instant getDateModification() {
        return dateModification;
    }
}
