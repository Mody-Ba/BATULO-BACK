package com.batulo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RG-06 : tant que le mot de passe temporaire n'est pas changé, seules les routes /api/auth/** sont permises. */
class MotDePasseTemporaireIntegrationTest extends IntegrationTestBase {

    @Test
    void gestionnaireDoitChangerSonMotDePasseTemporaireAvantToutAutreAcces() throws Exception {
        adminActif("awa@test.com");
        postAvec(jeton("awa@test.com"), "/api/admin/gestionnaires",
                Map.of("prenom", "Gora", "nom", "Ba", "courriel", "gora@test.com"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.motDePasseTemporaire").value(true));

        // Le mot de passe temporaire n'est jamais renvoyé par l'API : on le lit dans le courriel (mock).
        ArgumentCaptor<String> motDePasse = ArgumentCaptor.forClass(String.class);
        verify(emailService).envoyerMotDePasseTemporaire(eq("gora@test.com"), anyString(), motDePasse.capture(), any());

        JsonNode session = connexion("gora@test.com", motDePasse.getValue());
        assertThat(session.get("motDePasseTemporaire").asBoolean()).isTrue();
        String jeton = session.get("jeton").asText();

        getAvec(jeton, "/api/notifications")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MOT_DE_PASSE_TEMPORAIRE"));
        getAvec(jeton, "/api/auth/moi").andExpect(status().isOk());

        JsonNode nouvelleSession = objectMapper.readTree(postAvec(jeton, "/api/auth/changer-mot-de-passe", Map.of(
                        "motDePasseActuel", motDePasse.getValue(),
                        "nouveauMotDePasse", "Gestion2026",
                        "confirmationMotDePasse", "Gestion2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motDePasseTemporaire").value(false))
                .andReturn().getResponse().getContentAsString());

        getAvec(nouvelleSession.get("jeton").asText(), "/api/notifications").andExpect(status().isOk());
        // Le filtre relit le compte : même l'ancien jeton d'accès n'est plus bloqué.
        getAvec(jeton, "/api/notifications").andExpect(status().isOk());
    }

    @Test
    void changementRefuseSiLeMotDePasseActuelEstFaux() throws Exception {
        adminActif("awa@test.com");

        postAvec(jeton("awa@test.com"), "/api/auth/changer-mot-de-passe", Map.of(
                        "motDePasseActuel", "Mauvais123",
                        "nouveauMotDePasse", "Nouveau2026",
                        "confirmationMotDePasse", "Nouveau2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MOT_DE_PASSE_ACTUEL_INCORRECT"));
    }
}
