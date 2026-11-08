package com.batulo.integration;

import com.batulo.entity.Utilisateur;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Réutilisation des formulaires : création à partir d'un sondage existant, modèles, liste des formulaires. */
class SondageReutilisationIntegrationTest extends PostgresIntegrationTestBase {

    private String jeton;

    @BeforeEach
    void connecterAdmin() throws Exception {
        adminActif("awa@test.com");
        jeton = jeton("awa@test.com");
    }

    @Test
    void creerAPartirDUnSondageTermineCopieLeContenuSansToucherLOriginal() throws Exception {
        long sourceId = sondageAvecContenu(jeton, "Enquête 2026");
        postAvec(jeton, "/api/sondages/" + sourceId + "/publier", Map.of()).andExpect(status().isOk());
        postAvec(jeton, "/api/sondages/" + sourceId + "/fermer", Map.of()).andExpect(status().isOk());
        String sourceAvant = getAvec(jeton, "/api/sondages/" + sourceId).andReturn().getResponse().getContentAsString();

        JsonNode nouveau = lire(postAvec(jeton, "/api/sondages",
                Map.of("titre", "Enquête 2027", "sondageSourceId", sourceId)).andExpect(status().isCreated()));

        // Nouveau brouillon : contenu copié, mêmes valeurs d'options, rien d'autre.
        assertThat(nouveau.get("titre").asText()).isEqualTo("Enquête 2027");
        assertThat(nouveau.get("description").asText()).isEqualTo("Description de la source");
        assertThat(nouveau.get("sondageOrigineId").asLong()).isEqualTo(sourceId);
        assertThat(nouveau.get("statut").asText()).isEqualTo("NON_COMMENCE");
        assertThat(nouveau.get("publie").asBoolean()).isFalse();
        assertThat(nouveau.get("datePublication").isNull()).isTrue();
        assertThat(nouveau.get("dateFermeture").isNull()).isTrue();
        assertThat(nouveau.get("version").asInt()).isEqualTo(1);
        assertThat(nouveau.get("estModele").asBoolean()).isFalse();
        JsonNode source = objectMapper.readTree(sourceAvant);
        assertThat(nouveau.at("/sections/0/questions/0/options/1/valeur"))
                .isEqualTo(source.at("/sections/0/questions/0/options/1/valeur"));
        assertThat(nouveau.at("/sections/0/questions/0/options/1/id"))
                .isNotEqualTo(source.at("/sections/0/questions/0/options/1/id"));
        assertThat(nouveau.at("/sections/0/questions/1/configuration"))
                .isEqualTo(source.at("/sections/0/questions/1/configuration"));

        // Le nouveau se modifie ; l'original reste exactement le même.
        long nouveauId = nouveau.get("id").asLong();
        postAvec(jeton, "/api/sondages/" + nouveauId + "/sections", Map.of("titre", "Ajout")).andExpect(status().isCreated());
        assertThat(getAvec(jeton, "/api/sondages/" + sourceId).andReturn().getResponse().getContentAsString())
                .isEqualTo(sourceAvant);
        // Ce n'est pas une version de la source : chacun a sa propre lignée.
        getAvec(jeton, "/api/sondages/" + nouveauId + "/versions").andExpect(jsonPath("$", hasSize(1)));
        getAvec(jeton, "/api/sondages/" + sourceId + "/versions").andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void creerVideResteLeCasParDefaut() throws Exception {
        postAvec(jeton, "/api/sondages", Map.of("titre", "Vide"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sections", hasSize(0)))
                .andExpect(jsonPath("$.sondageOrigineId").doesNotExist());
        postAvec(jeton, "/api/sondages", Map.of("titre", "Source inconnue", "sondageSourceId", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SONDAGE_INTROUVABLE"));
    }

    @Test
    void marquerCommeModeleEtListerLesFormulaires() throws Exception {
        long publie = sondageAvecContenu(jeton, "Modèle agricole");
        postAvec(jeton, "/api/sondages/" + publie + "/publier", Map.of()).andExpect(status().isOk());
        sondageAvecContenu(jeton, "Brouillon ordinaire");

        // Marquer un sondage publié : permis (métadonnée, pas du contenu).
        putAvec(jeton, "/api/sondages/" + publie + "/modele", Map.of("estModele", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estModele").value(true));
        putAvec(jeton, "/api/sondages/" + publie + "/modele", Map.of())
                .andExpect(status().isBadRequest());

        getAvec(jeton, "/api/sondages/formulaires")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.contenu[0].titre").value("Modèle agricole"))
                .andExpect(jsonPath("$.contenu[0].estModele").value(true))
                .andExpect(jsonPath("$.contenu[0].nombreQuestions").value(2))
                .andExpect(jsonPath("$.contenu[0].dateCreation").exists());
        getAvec(jeton, "/api/sondages/formulaires?modelesSeulement=true")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.contenu[0].id").value(publie));

        // Le modèle n'est pas recopié dans le nouveau sondage.
        postAvec(jeton, "/api/sondages", Map.of("titre", "Depuis le modèle", "sondageSourceId", publie))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estModele").value(false));

        putAvec(jeton, "/api/sondages/" + publie + "/modele", Map.of("estModele", false)).andExpect(status().isOk());
        getAvec(jeton, "/api/sondages/formulaires?modelesSeulement=true").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void droitsDeReutilisation() throws Exception {
        Utilisateur admin = utilisateurRepository.findByCourriel("awa@test.com").orElseThrow();
        gestionnaire("g1@test.com", admin, true);
        gestionnaire("g2@test.com", admin, true);
        Utilisateur sansPermission = gestionnaire("g3@test.com", admin, false);
        String jetonG1 = jeton("g1@test.com");
        long sondageAdmin = sondageAvecContenu(jeton, "De l'admin");
        long sondageG1 = sondageAvecContenu(jetonG1, "De G1");

        // L'admin réutilise tous les formulaires de l'entreprise.
        postAvec(jeton, "/api/sondages", Map.of("titre", "A", "sondageSourceId", sondageG1)).andExpect(status().isCreated());
        getAvec(jeton, "/api/sondages/formulaires").andExpect(jsonPath("$.totalElements").value(3));

        // Le gestionnaire réutilise ceux qu'il voit, pas les autres.
        postAvec(jetonG1, "/api/sondages", Map.of("titre", "B", "sondageSourceId", sondageG1)).andExpect(status().isCreated());
        postAvec(jetonG1, "/api/sondages", Map.of("titre", "C", "sondageSourceId", sondageAdmin))
                .andExpect(status().isNotFound());
        getAvec(jetonG1, "/api/sondages/formulaires").andExpect(jsonPath("$.totalElements").value(2));
        getAvec(jeton("g2@test.com"), "/api/sondages/formulaires").andExpect(jsonPath("$.totalElements").value(0));

        // Sans la permission de créer : ni liste ni réutilisation.
        String jetonG3 = jeton(sansPermission.getCourriel());
        getAvec(jetonG3, "/api/sondages/formulaires")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_SONDAGES_REQUISE"));
        putAvec(jetonG1, "/api/sondages/" + sondageAdmin + "/modele", Map.of("estModele", true))
                .andExpect(status().isNotFound());

        // Une autre entreprise ne réutilise rien.
        adminActif("b@test.com");
        postAvec(jeton("b@test.com"), "/api/sondages", Map.of("titre", "D", "sondageSourceId", sondageAdmin))
                .andExpect(status().isNotFound());
    }

    /** Sondage avec une section, une question à choix et une échelle. */
    private long sondageAvecContenu(String jeton, String titre) throws Exception {
        Map<String, Object> creation = new HashMap<>(Map.of("titre", titre, "description", "Description de la source"));
        long id = lire(postAvec(jeton, "/api/sondages", creation).andExpect(status().isCreated())).get("id").asLong();
        long sectionId = lire(postAvec(jeton, "/api/sondages/" + id + "/sections", Map.of("titre", "Section")))
                .at("/sections/0/id").asLong();
        postAvec(jeton, "/api/sondages/" + id + "/sections/" + sectionId + "/questions",
                SondageIntegrationTest.question("Céréale", "CHOIX_UNIQUE", null, "Mil", "Maïs"))
                .andExpect(status().isCreated());
        postAvec(jeton, "/api/sondages/" + id + "/sections/" + sectionId + "/questions",
                SondageIntegrationTest.question("Satisfaction", "ECHELLE", Map.of("max", 10)))
                .andExpect(status().isCreated());
        return id;
    }

    private JsonNode lire(ResultActions resultat) throws Exception {
        return objectMapper.readTree(resultat.andReturn().getResponse().getContentAsString());
    }
}
