package com.batulo.entity;

import com.batulo.enums.Formule;
import com.batulo.enums.StatutDemande;
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
import jakarta.persistence.Table;
import java.time.Instant;

/** Demande d'augmentation d'abonnement d'un admin au super admin (AD-05, SA-07). Sans effet sur l'abonnement : acceptée, l'admin paie lui-même la formule (RG-09). */
@Entity
@Table(name = "demande_abonnement")
public class DemandeAbonnement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entreprise_id", nullable = false)
    private Entreprise entreprise;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private Utilisateur admin;

    @Enumerated(EnumType.STRING)
    @Column(name = "formule_souhaitee", nullable = false, length = 20)
    private Formule formuleSouhaitee;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutDemande statut;

    /** Réponse du super admin. */
    @Column(columnDefinition = "TEXT")
    private String reponse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "traitee_par_id")
    private Utilisateur traiteePar;

    @Column(name = "date_demande", nullable = false, updatable = false)
    private Instant dateDemande;

    @Column(name = "date_traitement")
    private Instant dateTraitement;

    public DemandeAbonnement() {
    }

    @PrePersist
    void avantCreation() {
        dateDemande = Instant.now();
        if (statut == null) {
            statut = StatutDemande.EN_ATTENTE;
        }
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

    public Utilisateur getAdmin() {
        return admin;
    }

    public void setAdmin(Utilisateur admin) {
        this.admin = admin;
    }

    public Formule getFormuleSouhaitee() {
        return formuleSouhaitee;
    }

    public void setFormuleSouhaitee(Formule formuleSouhaitee) {
        this.formuleSouhaitee = formuleSouhaitee;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public StatutDemande getStatut() {
        return statut;
    }

    public void setStatut(StatutDemande statut) {
        this.statut = statut;
    }

    public String getReponse() {
        return reponse;
    }

    public void setReponse(String reponse) {
        this.reponse = reponse;
    }

    public Utilisateur getTraiteePar() {
        return traiteePar;
    }

    public void setTraiteePar(Utilisateur traiteePar) {
        this.traiteePar = traiteePar;
    }

    public Instant getDateDemande() {
        return dateDemande;
    }

    public Instant getDateTraitement() {
        return dateTraitement;
    }

    public void setDateTraitement(Instant dateTraitement) {
        this.dateTraitement = dateTraitement;
    }
}
