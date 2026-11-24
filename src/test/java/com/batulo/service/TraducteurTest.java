package com.batulo.service;

import com.batulo.entity.OptionQuestion;
import com.batulo.entity.Question;
import com.batulo.entity.Sondage;
import com.batulo.enums.Langue;
import com.batulo.enums.TypeQuestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Résolution des textes dans une langue : traduction, secours sur le texte principal, langue non proposée. */
class TraducteurTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void traductionOuSecours() throws Exception {
        Sondage sondage = sondage();
        Traducteur en = Traducteur.pour(sondage, Langue.EN);
        assertThat(en.langue()).isEqualTo(Langue.EN);
        assertThat(en.titre(sondage)).isEqualTo("Satisfaction survey");
        // Traduction vide ou absente : texte principal.
        assertThat(en.description(sondage)).isEqualTo("Avis");
        assertThat(en.objetEnquete(sondage)).isNull();

        Question question = new Question();
        question.setType(TypeQuestion.MATRICE);
        question.setLibelle("Services");
        question.setConfiguration(json.readTree("{\"lignes\":[\"Eau\",\"Santé\"],\"colonnes\":[\"Bon\",\"Mauvais\"]}"));
        question.setTraductions(json.readTree("{\"en\":{\"lignes\":{\"Eau\":\"Water\"},\"libelle\":\"Services EN\"}}"));
        assertThat(en.libelle(question)).isEqualTo("Services EN");
        assertThat(en.matrice(question, "lignes", "Eau")).isEqualTo("Water");
        assertThat(en.matrice(question, "lignes", "Santé")).isEqualTo("Santé");

        OptionQuestion option = new OptionQuestion();
        option.setLibelle("Oui");
        option.setTraductions(json.readTree("{\"en\":{\"libelle\":\"Yes\"}}"));
        assertThat(en.libelle(option)).isEqualTo("Yes");
        assertThat(Traducteur.pour(sondage, Langue.FR).libelle(option)).isEqualTo("Oui");
    }

    @Test
    void langueNonProposeeOuPrincipale() throws Exception {
        Sondage sondage = sondage();
        // Arabe non proposé : langue principale.
        Traducteur ar = Traducteur.pour(sondage, Langue.AR);
        assertThat(ar.langue()).isEqualTo(Langue.FR);
        assertThat(ar.estPrincipale()).isTrue();
        assertThat(ar.titre(sondage)).isEqualTo("Enquête de satisfaction");
        assertThat(Traducteur.pour(sondage, null).langue()).isEqualTo(Langue.FR);

        // Langue principale changée en anglais (étiquette seulement) : la traduction « en » n'est plus lue.
        sondage.setLanguePrincipale(Langue.EN);
        sondage.setLanguesSupplementaires(List.of(Langue.FR));
        assertThat(Traducteur.pour(sondage, Langue.EN).titre(sondage)).isEqualTo("Enquête de satisfaction");
        assertThat(sondage.getLanguesDisponibles()).containsExactly(Langue.EN, Langue.FR);
    }

    private Sondage sondage() throws Exception {
        Sondage sondage = new Sondage();
        sondage.setTitre("Enquête de satisfaction");
        sondage.setDescription("Avis");
        sondage.setLanguesSupplementaires(List.of(Langue.EN));
        sondage.setTraductions(json.readTree("{\"en\":{\"titre\":\"Satisfaction survey\",\"description\":\"  \"}}"));
        return sondage;
    }
}
