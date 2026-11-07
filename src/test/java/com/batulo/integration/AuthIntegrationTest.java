package com.batulo.integration;

import com.batulo.entity.Utilisateur;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Connexion, 401 sans jeton, jetons de rafraîchissement, mot de passe oublié, CORS. */
class AuthIntegrationTest extends IntegrationTestBase {

    @Test
    void connexionRenvoieLesJetonsLeRoleEtLeStatut() throws Exception {
        adminActif("awa@test.com");

        JsonNode reponse = connexion("AWA@test.com", MOT_DE_PASSE);

        assertThat(reponse.get("jeton").asText()).isNotBlank();
        assertThat(reponse.get("jetonRafraichissement").asText()).isNotBlank();
        assertThat(reponse.get("typeJeton").asText()).isEqualTo("Bearer");
        assertThat(reponse.get("expireDans").asLong()).isEqualTo(900);
        assertThat(reponse.get("role").asText()).isEqualTo("ADMIN");
        assertThat(reponse.get("statutAbonnement").asText()).isEqualTo("ACTIF");
        assertThat(reponse.get("motDePasseTemporaire").asBoolean()).isFalse();
    }

    @Test
    void mauvaisMotDePasseEtCourrielInconnuDonnentLaMemeReponse() throws Exception {
        adminActif("awa@test.com");

        for (String courriel : new String[]{"awa@test.com", "inconnu@test.com"}) {
            mockMvc.perform(post("/api/auth/connexion").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("courriel", courriel, "motDePasse", "Mauvais123"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("IDENTIFIANTS_INVALIDES"))
                    .andExpect(jsonPath("$.message").value("Courriel ou mot de passe incorrect."));
        }
    }

    @Test
    void routeProtegeeSansJetonOuAvecJetonFalsifieDonne401() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NON_AUTHENTIFIE"));

        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NON_AUTHENTIFIE"))
                .andExpect(jsonPath("$.message").value("Jeton invalide ou expiré."));
    }

    @Test
    void routesPubliquesSansJeton() throws Exception {
        mockMvc.perform(get("/api/abonnements/formules")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void moiRenvoieLeCompteDuJeton() throws Exception {
        Utilisateur admin = adminActif("awa@test.com");

        getAvec(jeton("awa@test.com"), "/api/auth/moi")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.utilisateur.id").value(admin.getId()))
                .andExpect(jsonPath("$.utilisateur.role").value("ADMIN"))
                .andExpect(jsonPath("$.statutAbonnement").value("ACTIF"));
    }

    @Test
    void rafraichirFaitTournerLeJetonEtUneReutilisationFermeToutesLesSessions() throws Exception {
        adminActif("awa@test.com");
        String ancien = connexion("awa@test.com", MOT_DE_PASSE).get("jetonRafraichissement").asText();

        JsonNode rafraichi = objectMapper.readTree(mockMvc.perform(post("/api/auth/rafraichir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("jetonRafraichissement", ancien))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String nouveau = rafraichi.get("jetonRafraichissement").asText();
        assertThat(nouveau).isNotEqualTo(ancien);

        // L'ancien jeton revient : il a pu être volé, toutes les sessions sont fermées.
        rafraichir(ancien).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("JETON_RAFRAICHISSEMENT_INVALIDE"));
        rafraichir(nouveau).andExpect(status().isUnauthorized());
    }

    @Test
    void deconnexionRevoqueLeJetonDeRafraichissement() throws Exception {
        adminActif("awa@test.com");
        JsonNode session = connexion("awa@test.com", MOT_DE_PASSE);
        String rafraichissement = session.get("jetonRafraichissement").asText();

        postAvec(session.get("jeton").asText(), "/api/auth/deconnexion",
                Map.of("jetonRafraichissement", rafraichissement))
                .andExpect(status().isNoContent());

        rafraichir(rafraichissement).andExpect(status().isUnauthorized());
    }

    @Test
    void motDePasseOublieEnvoieUnLienAUsageUnique() throws Exception {
        adminActif("awa@test.com");

        mockMvc.perform(post("/api/auth/mot-de-passe-oublie").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("courriel", "awa@test.com"))))
                .andExpect(status().isOk());
        ArgumentCaptor<String> lien = ArgumentCaptor.forClass(String.class);
        verify(emailService).envoyerLienReinitialisation(eq("awa@test.com"), anyString(), lien.capture(), any());
        String jetonLien = lien.getValue().substring(lien.getValue().indexOf("jeton=") + "jeton=".length());

        Map<String, String> reinitialisation = Map.of("jeton", jetonLien,
                "nouveauMotDePasse", "Nouveau2026", "confirmationMotDePasse", "Nouveau2026");
        mockMvc.perform(post("/api/auth/reinitialiser-mot-de-passe").contentType(MediaType.APPLICATION_JSON)
                        .content(json(reinitialisation)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/reinitialiser-mot-de-passe").contentType(MediaType.APPLICATION_JSON)
                        .content(json(reinitialisation)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("JETON_INVALIDE"));

        connexion("awa@test.com", "Nouveau2026");
    }

    @Test
    void motDePasseOublieAvecUnCourrielInconnuRepondPareilSansEnvoyer() throws Exception {
        mockMvc.perform(post("/api/auth/mot-de-passe-oublie").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("courriel", "personne@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
        verify(emailService, never()).envoyerLienReinitialisation(anyString(), anyString(), anyString(), any());
    }

    @Test
    void motDePasseTemporaireExpireApres24h() throws Exception {
        Utilisateur admin = adminActif("awa@test.com");
        admin.setMotDePasseTemporaire(true);
        admin.setMotDePasseTemporaireExpireLe(Instant.now().minus(Duration.ofMinutes(1)));
        utilisateurRepository.save(admin);

        mockMvc.perform(post("/api/auth/connexion").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("courriel", "awa@test.com", "motDePasse", MOT_DE_PASSE))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MOT_DE_PASSE_TEMPORAIRE_EXPIRE"));
    }

    @Test
    void corsAutoriseSeulementLeFrontendAngular() throws Exception {
        mockMvc.perform(options("/api/notifications")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
        mockMvc.perform(options("/api/notifications")
                        .header(HttpHeaders.ORIGIN, "http://pirate.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions rafraichir(String jeton) throws Exception {
        return mockMvc.perform(post("/api/auth/rafraichir").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("jetonRafraichissement", jeton))));
    }
}
