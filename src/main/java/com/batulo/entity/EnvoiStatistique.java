package com.batulo.entity;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Statistiques d'un sondage terminé envoyées par un gestionnaire à son admin (GE-06, RG-04), avec les filtres appliqués. */
@Entity
@Table(name = "envoi_statistique")
public class EnvoiStatistique {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Version envoyée (point de départ si lignee). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sondage_id", nullable = false)
    private Sondage sondage;

    /** true : toutes les versions du sondage. */
    @Column(nullable = false)
    private boolean lignee;

    /** {du, au, granularite, criteres: {cle: [valeurs]}, descriptionCriteres: [...]}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode filtres;

    /** Null si le compte du gestionnaire a été supprimé. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gestionnaire_id")
    private Utilisateur gestionnaire;

    /** GE-06 : le nom de l'expéditeur reste enregistré. */
    @Column(name = "expediteur_nom", nullable = false, length = 201)
    private String expediteurNom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private Utilisateur admin;

    @Column(columnDefinition = "TEXT")
    private String commentaire;

    @Column(name = "date_envoi", nullable = false, updatable = false)
    private Instant dateEnvoi;

    /** Première ouverture par l'admin. */
    @Column(name = "consulte_le")
    private Instant consulteLe;

    public EnvoiStatistique() {
    }

    @PrePersist
    void avantCreation() {
        dateEnvoi = Instant.now();
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

    public boolean isLignee() {
        return lignee;
    }

    public void setLignee(boolean lignee) {
        this.lignee = lignee;
    }

    public JsonNode getFiltres() {
        return filtres;
    }

    public void setFiltres(JsonNode filtres) {
        this.filtres = filtres;
    }

    public Utilisateur getGestionnaire() {
        return gestionnaire;
    }

    public void setGestionnaire(Utilisateur gestionnaire) {
        this.gestionnaire = gestionnaire;
    }

    public String getExpediteurNom() {
        return expediteurNom;
    }

    public void setExpediteurNom(String expediteurNom) {
        this.expediteurNom = expediteurNom;
    }

    public Utilisateur getAdmin() {
        return admin;
    }

    public void setAdmin(Utilisateur admin) {
        this.admin = admin;
    }

    public String getCommentaire() {
        return commentaire;
    }

    public void setCommentaire(String commentaire) {
        this.commentaire = commentaire;
    }

    public Instant getDateEnvoi() {
        return dateEnvoi;
    }

    public Instant getConsulteLe() {
        return consulteLe;
    }

    public void setConsulteLe(Instant consulteLe) {
        this.consulteLe = consulteLe;
    }
}
