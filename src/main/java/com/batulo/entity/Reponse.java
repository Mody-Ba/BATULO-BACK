package com.batulo.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Réponse d'un répondant à une version d'un sondage (RE-02). Anonyme : aucune adresse IP n'est conservée. */
@Entity
@Table(name = "reponse")
public class Reponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Version à laquelle le répondant a répondu. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sondage_id", nullable = false)
    private Sondage sondage;

    /** Version 1 de la lignée. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lignee_id", nullable = false)
    private Sondage lignee;

    @Column(name = "date_soumission", nullable = false, updatable = false)
    private Instant dateSoumission;

    /** Hash SHA-256 du jeton de l'appareil ; null en collecte terrain. */
    @Column(name = "jeton_repondant_hash", length = 64)
    private String jetonRepondantHash;

    /** Heure (serveur) du consentement, si la version l'exigeait ; null sinon. */
    @Column(name = "date_consentement")
    private Instant dateConsentement;

    @OneToMany(mappedBy = "reponse", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReponseQuestion> valeurs = new ArrayList<>();

    /** Questions masquées par une condition d'affichage (ids) : les statistiques en déduisent qui les a vues. */
    @ElementCollection
    @CollectionTable(name = "reponse_question_masquee", joinColumns = @JoinColumn(name = "reponse_id"))
    @Column(name = "question_id", nullable = false)
    private Set<Long> questionsMasquees = new HashSet<>();

    public Reponse() {
    }

    @PrePersist
    void avantCreation() {
        if (dateSoumission == null) {
            dateSoumission = Instant.now();
        }
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

    public Sondage getLignee() {
        return lignee;
    }

    public void setLignee(Sondage lignee) {
        this.lignee = lignee;
    }

    public Instant getDateSoumission() {
        return dateSoumission;
    }

    /** Réservé aux réponses de test générées en profil dev (dates étalées dans le passé). */
    public void setDateSoumission(Instant dateSoumission) {
        this.dateSoumission = dateSoumission;
    }

    public String getJetonRepondantHash() {
        return jetonRepondantHash;
    }

    public void setJetonRepondantHash(String jetonRepondantHash) {
        this.jetonRepondantHash = jetonRepondantHash;
    }

    public List<ReponseQuestion> getValeurs() {
        return valeurs;
    }

    public Instant getDateConsentement() {
        return dateConsentement;
    }

    public void setDateConsentement(Instant dateConsentement) {
        this.dateConsentement = dateConsentement;
    }

    public Set<Long> getQuestionsMasquees() {
        return questionsMasquees;
    }
}
