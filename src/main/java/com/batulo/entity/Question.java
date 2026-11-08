package com.batulo.entity;

import com.batulo.enums.TypeQuestion;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Question d'une section. Sa configuration (JSONB) dépend du type et est validée par le serveur. */
@Entity
@Table(name = "question")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private Section section;

    @Column(nullable = false, length = 500)
    private String libelle;

    /** Texte d'aide affiché sous la question (facultatif). */
    @Column(length = 500)
    private String aide;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TypeQuestion type;

    @Column(nullable = false)
    private boolean obligatoire;

    /** 1, 2, 3... dans la section, attribué par le serveur. */
    @Column(nullable = false)
    private int ordre;

    /** Ex. ECHELLE {"min":1,"max":5}, MATRICE {"lignes":[...],"colonnes":[...]} ; {} pour les autres types. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode configuration;

    /**
     * Clé stable de la question, recopiée dans les versions et les copies : les statistiques d'une lignée
     * regroupent les questions de même clé. Ne change jamais, même si le libellé change.
     */
    @Column(nullable = false, length = 36, updatable = false)
    private String cle;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordre ASC")
    private List<OptionQuestion> options = new ArrayList<>();

    public Question() {
    }

    @PrePersist
    void avantCreation() {
        if (cle == null) {
            cle = UUID.randomUUID().toString();
        }
    }

    public String getCle() {
        return cle;
    }

    public void setCle(String cle) {
        this.cle = cle;
    }

    public Long getId() {
        return id;
    }

    public Section getSection() {
        return section;
    }

    public void setSection(Section section) {
        this.section = section;
    }

    public String getLibelle() {
        return libelle;
    }

    public void setLibelle(String libelle) {
        this.libelle = libelle;
    }

    public String getAide() {
        return aide;
    }

    public void setAide(String aide) {
        this.aide = aide;
    }

    public TypeQuestion getType() {
        return type;
    }

    public void setType(TypeQuestion type) {
        this.type = type;
    }

    public boolean isObligatoire() {
        return obligatoire;
    }

    public void setObligatoire(boolean obligatoire) {
        this.obligatoire = obligatoire;
    }

    public int getOrdre() {
        return ordre;
    }

    public void setOrdre(int ordre) {
        this.ordre = ordre;
    }

    public JsonNode getConfiguration() {
        return configuration;
    }

    public void setConfiguration(JsonNode configuration) {
        this.configuration = configuration;
    }

    public List<OptionQuestion> getOptions() {
        return options;
    }
}
