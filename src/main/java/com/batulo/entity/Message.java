package com.batulo.entity;

import com.batulo.enums.CoteConversation;
import com.batulo.enums.TypeMessage;
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

/** Message d'une conversation, en texte brut (le frontend l'échappe à l'affichage). Pas de pièce jointe ni de suppression. */
@Entity
@Table(name = "message")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    /** Null si le compte de l'expéditeur a été supprimé. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expediteur_id")
    private Utilisateur expediteur;

    /** Nom au moment de l'envoi. */
    @Column(name = "expediteur_nom", nullable = false, length = 201)
    private String expediteurNom;

    /** Côté de l'expéditeur : le message est « reçu » par l'autre côté. */
    @Enumerated(EnumType.STRING)
    @Column(name = "cote", nullable = false, length = 20)
    private CoteConversation cote;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private TypeMessage type;

    /** Envoi de statistiques ou demande d'abonnement liée. */
    @Column(name = "reference_id")
    private Long referenceId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenu;

    @Column(name = "date_envoi", nullable = false, updatable = false)
    private Instant dateEnvoi;

    /** Lu par l'autre côté ; null tant qu'il ne l'a pas lu. */
    @Column(name = "lu_le")
    private Instant luLe;

    public Message() {
    }

    @PrePersist
    void avantCreation() {
        dateEnvoi = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    public Utilisateur getExpediteur() {
        return expediteur;
    }

    public void setExpediteur(Utilisateur expediteur) {
        this.expediteur = expediteur;
    }

    public String getExpediteurNom() {
        return expediteurNom;
    }

    public void setExpediteurNom(String expediteurNom) {
        this.expediteurNom = expediteurNom;
    }

    public CoteConversation getCote() {
        return cote;
    }

    public void setCote(CoteConversation cote) {
        this.cote = cote;
    }

    public TypeMessage getType() {
        return type;
    }

    public void setType(TypeMessage type) {
        this.type = type;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(Long referenceId) {
        this.referenceId = referenceId;
    }

    public String getContenu() {
        return contenu;
    }

    public void setContenu(String contenu) {
        this.contenu = contenu;
    }

    public Instant getDateEnvoi() {
        return dateEnvoi;
    }

    public Instant getLuLe() {
        return luLe;
    }

    public void setLuLe(Instant luLe) {
        this.luLe = luLe;
    }
}
