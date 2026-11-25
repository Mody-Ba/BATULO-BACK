package com.batulo.entity;

import com.batulo.enums.Langue;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 * Personne invitée à répondre à un sondage, avec un lien personnel à usage unique. Porte sur la lignée (version 1) :
 * toutes les versions partagent la liste, une seule réponse par invité. Le jeton n'est jamais stocké : il est
 * recalculé à partir du sel (HMAC du secret serveur) et retrouvé par son SHA-256.
 */
@Entity
@Table(name = "invitation")
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Version 1 de la lignée. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lignee_id", nullable = false)
    private Sondage lignee;

    @Column(length = 100)
    private String prenom;

    @Column(length = 100)
    private String nom;

    /** En minuscules, unique dans la lignée. */
    @Column(nullable = false, length = 254)
    private String courriel;

    @Column(length = 30)
    private String telephone;

    /** Langue préférée ; null : langue principale du sondage. */
    @Convert(converter = LangueConverter.class)
    @Column(length = 2)
    private Langue langue;

    @Column(name = "jeton_hash", nullable = false, length = 64)
    private String jetonHash;

    @Column(name = "jeton_sel", nullable = false, length = 43)
    private String jetonSel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cree_par_id")
    private Utilisateur creePar;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private Instant dateCreation;

    @Column(name = "premier_envoi_le")
    private Instant premierEnvoiLe;

    @Column(name = "dernier_envoi_le")
    private Instant dernierEnvoiLe;

    @Column(name = "derniere_relance_le")
    private Instant derniereRelanceLe;

    @Column(name = "nombre_envois", nullable = false)
    private int nombreEnvois;

    @Column(name = "envoi_en_file_le")
    private Instant envoiEnFileLe;

    @Column(name = "echec_envoi_le")
    private Instant echecEnvoiLe;

    @Column(name = "ouverte_le")
    private Instant ouverteLe;

    /** Mode anonyme : arrondie au jour. */
    @Column(name = "repondu_le")
    private Instant reponduLe;

    @Column(name = "revoquee_le")
    private Instant revoqueeLe;

    public Invitation() {
    }

    @PrePersist
    void avantCreation() {
        if (dateCreation == null) {
            dateCreation = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Sondage getLignee() {
        return lignee;
    }

    public void setLignee(Sondage lignee) {
        this.lignee = lignee;
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

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public Langue getLangue() {
        return langue;
    }

    public void setLangue(Langue langue) {
        this.langue = langue;
    }

    public String getJetonHash() {
        return jetonHash;
    }

    public void setJetonHash(String jetonHash) {
        this.jetonHash = jetonHash;
    }

    public String getJetonSel() {
        return jetonSel;
    }

    public void setJetonSel(String jetonSel) {
        this.jetonSel = jetonSel;
    }

    public Utilisateur getCreePar() {
        return creePar;
    }

    public void setCreePar(Utilisateur creePar) {
        this.creePar = creePar;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }

    public Instant getPremierEnvoiLe() {
        return premierEnvoiLe;
    }

    public void setPremierEnvoiLe(Instant premierEnvoiLe) {
        this.premierEnvoiLe = premierEnvoiLe;
    }

    public Instant getDernierEnvoiLe() {
        return dernierEnvoiLe;
    }

    public void setDernierEnvoiLe(Instant dernierEnvoiLe) {
        this.dernierEnvoiLe = dernierEnvoiLe;
    }

    public Instant getDerniereRelanceLe() {
        return derniereRelanceLe;
    }

    public void setDerniereRelanceLe(Instant derniereRelanceLe) {
        this.derniereRelanceLe = derniereRelanceLe;
    }

    public int getNombreEnvois() {
        return nombreEnvois;
    }

    public void setNombreEnvois(int nombreEnvois) {
        this.nombreEnvois = nombreEnvois;
    }

    public Instant getEnvoiEnFileLe() {
        return envoiEnFileLe;
    }

    public void setEnvoiEnFileLe(Instant envoiEnFileLe) {
        this.envoiEnFileLe = envoiEnFileLe;
    }

    public Instant getEchecEnvoiLe() {
        return echecEnvoiLe;
    }

    public void setEchecEnvoiLe(Instant echecEnvoiLe) {
        this.echecEnvoiLe = echecEnvoiLe;
    }

    public Instant getOuverteLe() {
        return ouverteLe;
    }

    public void setOuverteLe(Instant ouverteLe) {
        this.ouverteLe = ouverteLe;
    }

    public Instant getReponduLe() {
        return reponduLe;
    }

    public void setReponduLe(Instant reponduLe) {
        this.reponduLe = reponduLe;
    }

    public Instant getRevoqueeLe() {
        return revoqueeLe;
    }

    public void setRevoqueeLe(Instant revoqueeLe) {
        this.revoqueeLe = revoqueeLe;
    }
}
