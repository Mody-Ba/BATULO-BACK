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
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Une valeur de réponse à une question, rangée dans la colonne de son type pour les statistiques :
 * option (CHOIX_UNIQUE, une ligne par option pour CHOIX_MULTIPLE), numérique (ECHELLE, NUMERIQUE), texte, date,
 * booléen (OUI_NON) ou JSON (CLASSEMENT, MATRICE).
 */
@Entity
@Table(name = "reponse_question")
public class ReponseQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reponse_id", nullable = false)
    private Reponse reponse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id")
    private OptionQuestion option;

    @Column(name = "valeur_numerique", precision = 19, scale = 4)
    private BigDecimal valeurNumerique;

    @Column(name = "valeur_texte", columnDefinition = "TEXT")
    private String valeurTexte;

    @Column(name = "valeur_date")
    private LocalDate valeurDate;

    @Column(name = "valeur_booleenne")
    private Boolean valeurBooleenne;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "valeur_json")
    private JsonNode valeurJson;

    public ReponseQuestion() {
    }

    public ReponseQuestion(Question question) {
        this.question = question;
    }

    public Long getId() {
        return id;
    }

    public Reponse getReponse() {
        return reponse;
    }

    public void setReponse(Reponse reponse) {
        this.reponse = reponse;
    }

    public Question getQuestion() {
        return question;
    }

    public void setQuestion(Question question) {
        this.question = question;
    }

    public OptionQuestion getOption() {
        return option;
    }

    public void setOption(OptionQuestion option) {
        this.option = option;
    }

    public BigDecimal getValeurNumerique() {
        return valeurNumerique;
    }

    public void setValeurNumerique(BigDecimal valeurNumerique) {
        this.valeurNumerique = valeurNumerique;
    }

    public String getValeurTexte() {
        return valeurTexte;
    }

    public void setValeurTexte(String valeurTexte) {
        this.valeurTexte = valeurTexte;
    }

    public LocalDate getValeurDate() {
        return valeurDate;
    }

    public void setValeurDate(LocalDate valeurDate) {
        this.valeurDate = valeurDate;
    }

    public Boolean getValeurBooleenne() {
        return valeurBooleenne;
    }

    public void setValeurBooleenne(Boolean valeurBooleenne) {
        this.valeurBooleenne = valeurBooleenne;
    }

    public JsonNode getValeurJson() {
        return valeurJson;
    }

    public void setValeurJson(JsonNode valeurJson) {
        this.valeurJson = valeurJson;
    }
}
