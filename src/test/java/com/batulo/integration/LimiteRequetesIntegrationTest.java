package com.batulo.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Limite de requêtes par adresse IP sur les routes publiques (429 TROP_DE_REQUETES). */
@TestPropertySource(properties = {
        "batulo.collecte.limite-lecture-par-minute=5",
        "batulo.collecte.limite-envoi-par-minute=3"})
class LimiteRequetesIntegrationTest extends PostgresIntegrationTestBase {

    @Test
    void auDelaDeLaLimiteLaRequeteEstRefusee() throws Exception {
        assertThat(premierRefus(false, 5)).isTrue();
        assertThat(premierRefus(true, 3)).isTrue();
    }

    @Test
    void lesRoutesAuthentifieesNeSontPasLimitees() throws Exception {
        for (int i = 0; i < 12; i++) {
            assertThat(mockMvc.perform(get("/api/abonnements/formules")).andReturn().getResponse().getStatus())
                    .isEqualTo(200);
        }
    }

    /**
     * Envoie des requêtes depuis la même adresse jusqu'au premier 429 : au moins « limite » passent avant. Robuste
     * au changement de minute (la fenêtre peut se remettre à zéro une fois pendant la boucle).
     */
    private boolean premierRefus(boolean envoi, int limite) throws Exception {
        int acceptees = 0;
        for (int i = 0; i < 2 * limite + 2; i++) {
            MvcResult resultat = mockMvc.perform(envoi
                            ? post("/api/public/sondages/inconnu/reponses").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reponses\":[]}").with(r -> { r.setRemoteAddr("203.0.113.7"); return r; })
                            : get("/api/public/sondages/inconnu").with(r -> { r.setRemoteAddr("203.0.113.8"); return r; }))
                    .andReturn();
            if (resultat.getResponse().getStatus() == 429) {
                assertThat(acceptees).isGreaterThanOrEqualTo(limite);
                assertThat(resultat.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
                assertThat(resultat.getResponse().getContentAsString()).contains("TROP_DE_REQUETES");
                return true;
            }
            acceptees++;
        }
        return false;
    }
}
