package com.batulo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Logo d'une entreprise (PNG ou JPEG), stocké en base. Table à part de {@link Entreprise} : l'image n'est lue que
 * lorsqu'on la sert ou qu'on l'intègre à un rapport. Le jeton change à chaque remplacement (URL versionnée).
 */
@Entity
@Table(name = "entreprise_logo")
public class EntrepriseLogo {

    @Id
    @Column(name = "entreprise_id")
    private Long entrepriseId;

    @Column(nullable = false, length = 36, unique = true)
    private String jeton;

    @Column(name = "type_mime", nullable = false, length = 20)
    private String typeMime;

    @Column(nullable = false)
    private int taille;

    @Column(nullable = false)
    private int largeur;

    @Column(nullable = false)
    private int hauteur;

    @Column(nullable = false)
    private byte[] contenu;

    @Column(name = "date_modification", nullable = false)
    private Instant dateModification;

    public EntrepriseLogo() {
    }

    public Long getEntrepriseId() {
        return entrepriseId;
    }

    public void setEntrepriseId(Long entrepriseId) {
        this.entrepriseId = entrepriseId;
    }

    public String getJeton() {
        return jeton;
    }

    public void setJeton(String jeton) {
        this.jeton = jeton;
    }

    public String getTypeMime() {
        return typeMime;
    }

    public void setTypeMime(String typeMime) {
        this.typeMime = typeMime;
    }

    public int getTaille() {
        return taille;
    }

    public void setTaille(int taille) {
        this.taille = taille;
    }

    public int getLargeur() {
        return largeur;
    }

    public void setLargeur(int largeur) {
        this.largeur = largeur;
    }

    public int getHauteur() {
        return hauteur;
    }

    public void setHauteur(int hauteur) {
        this.hauteur = hauteur;
    }

    public byte[] getContenu() {
        return contenu;
    }

    public void setContenu(byte[] contenu) {
        this.contenu = contenu;
    }

    public Instant getDateModification() {
        return dateModification;
    }

    public void setDateModification(Instant dateModification) {
        this.dateModification = dateModification;
    }
}
