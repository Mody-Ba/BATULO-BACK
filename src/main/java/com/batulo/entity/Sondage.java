package com.batulo.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Formulaire dynamique d'une entreprise (module 4.1) : une donnée construite par l'admin, pas du code.
 * Sondage > sections > questions > options, chaque niveau trié par « ordre ».
 */
@Entity
@Table(name = "sondage")
public class Sondage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entreprise_id", nullable = false)
    private Entreprise entreprise;

    @Column(nullable = false, length = 200)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Null si le compte créateur a été supprimé : le sondage reste à l'entreprise. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cree_par_id")
    private Utilisateur creePar;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private Instant dateCreation;

    @Column(name = "date_modification", nullable = false)
    private Instant dateModification;

    @OneToMany(mappedBy = "sondage", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordre ASC")
    private List<Section> sections = new ArrayList<>();

    public Sondage() {
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

    /** Une section ou une question a changé : la date de modification du sondage suit. */
    public void marquerModifie() {
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

    public String getTitre() {
        return titre;
    }

    public void setTitre(String titre) {
        this.titre = titre;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public Instant getDateModification() {
        return dateModification;
    }

    public List<Section> getSections() {
        return sections;
    }
}
