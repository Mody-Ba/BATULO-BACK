package com.batulo.entity;

import com.batulo.enums.TypeEnvoiInvitation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Journal des courriels d'invitation et de relance : compte la limite par heure et par entreprise. Les ids sont gardés
 * tels quels (sans relation chargée) ; l'invité peut être supprimé ensuite (invitation_id passe à null en base).
 */
@Entity
@Table(name = "invitation_envoi")
public class InvitationEnvoi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entreprise_id", nullable = false)
    private Long entrepriseId;

    @Column(name = "invitation_id")
    private Long invitationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypeEnvoiInvitation type;

    @Column(name = "date_envoi", nullable = false)
    private Instant dateEnvoi;

    @Column(nullable = false)
    private boolean succes;

    public InvitationEnvoi() {
    }

    public InvitationEnvoi(Long entrepriseId, Long invitationId, TypeEnvoiInvitation type, Instant dateEnvoi,
                           boolean succes) {
        this.entrepriseId = entrepriseId;
        this.invitationId = invitationId;
        this.type = type;
        this.dateEnvoi = dateEnvoi;
        this.succes = succes;
    }

    public Long getId() {
        return id;
    }

    public Long getEntrepriseId() {
        return entrepriseId;
    }

    public Long getInvitationId() {
        return invitationId;
    }

    public TypeEnvoiInvitation getType() {
        return type;
    }

    public Instant getDateEnvoi() {
        return dateEnvoi;
    }

    public boolean isSucces() {
        return succes;
    }
}
