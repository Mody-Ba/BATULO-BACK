package com.batulo.integration;

import com.batulo.entity.Utilisateur;
import com.fasterxml.jackson.databind.JsonNode;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Étape 3.4 : messagerie interne, envoi des statistiques (GE-06, RG-04), demandes d'abonnement (AD-05, SA-07). */
class MessagerieIntegrationTest extends PostgresIntegrationTestBase {

    private Utilisateur admin;
    private Utilisateur g1;
    private String jetonAdmin;
    private String jetonG1;

    @BeforeEach
    void comptes() throws Exception {
        admin = adminActif("awa@test.com");
        g1 = gestionnaire("g1@test.com", admin, true);
        gestionnaire("g2@test.com", admin, true);
        jdbcTemplate.update("update utilisateur set prenom = 'Moussa', nom = 'Diop' where id = ?", g1.getId());
        jdbcTemplate.update("update utilisateur set prenom = 'Awa', nom = 'Diallo' where id = ?", admin.getId());
        jetonAdmin = jeton("awa@test.com");
        jetonG1 = jeton("g1@test.com");
    }

    // ---------------------------------------------------------------- messagerie

    @Test
    void conversationAdminGestionnaire() throws Exception {
        // Le gestionnaire ouvre la conversation avec son admin (corps vide) ; l'admin la retrouve avec l'id du gestionnaire.
        long id = lire(postAvec(jetonG1, "/api/messagerie/conversations", Map.of()).andExpect(status().isOk()))
                .get("id").asLong();
        JsonNode cote = lire(postAvec(jetonAdmin, "/api/messagerie/conversations",
                Map.of("interlocuteurId", g1.getId())).andExpect(status().isOk()));
        assertThat(cote.get("id").asLong()).isEqualTo(id);
        assertThat(cote.at("/interlocuteur/prenom").asText()).isEqualTo("Moussa");
        assertThat(cote.get("dernierMessage").isNull()).isTrue();

        for (int i = 1; i <= 3; i++) {
            postAvec(jetonG1, "/api/messagerie/conversations/" + id + "/messages", Map.of("contenu", "Message " + i))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.expediteurNom").value("Moussa Diop"))
                    .andExpect(jsonPath("$.recu").value(false));
        }
        // Une seule notification « Nouveau message » pour la série de non lus.
        assertThat(notifications(admin.getId(), "NOUVEAU_MESSAGE")).isEqualTo(1);
        getAvec(jetonAdmin, "/api/messagerie/non-lus").andExpect(jsonPath("$.nonLues").value(3));
        JsonNode liste = lire(getAvec(jetonAdmin, "/api/messagerie/conversations"));
        assertThat(liste.at("/contenu/0/nonLus").asLong()).isEqualTo(3);
        assertThat(liste.at("/contenu/0/dernierMessage/extrait").asText()).isEqualTo("Message 3");
        assertThat(liste.at("/contenu/0/dernierMessage/recu").asBoolean()).isTrue();

        // Pagination, les plus récents d'abord.
        JsonNode page = lire(getAvec(jetonAdmin, "/api/messagerie/conversations/" + id + "/messages?taille=2"));
        assertThat(page.get("totalElements").asLong()).isEqualTo(3);
        assertThat(page.at("/contenu/0/contenu").asText()).isEqualTo("Message 3");
        assertThat(page.at("/contenu/0/recu").asBoolean()).isTrue();

        postAvec(jetonAdmin, "/api/messagerie/conversations/" + id + "/lu", Map.of())
                .andExpect(jsonPath("$.nombreMarquees").value(3));
        getAvec(jetonAdmin, "/api/messagerie/non-lus").andExpect(jsonPath("$.nonLues").value(0));
        // Le gestionnaire voit ses messages lus ; ses propres messages ne comptent pas comme non lus pour lui.
        getAvec(jetonG1, "/api/messagerie/non-lus").andExpect(jsonPath("$.nonLues").value(0));
        assertThat(lire(getAvec(jetonG1, "/api/messagerie/conversations/" + id + "/messages")).at("/contenu/0/luLe")
                .isNull()).isFalse();

        // Après lecture, un nouveau message notifie de nouveau.
        postAvec(jetonG1, "/api/messagerie/conversations/" + id + "/messages", Map.of("contenu", "Encore"));
        assertThat(notifications(admin.getId(), "NOUVEAU_MESSAGE")).isEqualTo(2);
        // Réponse de l'admin : le gestionnaire est notifié.
        postAvec(jetonAdmin, "/api/messagerie/conversations/" + id + "/messages", Map.of("contenu", "Bien reçu"))
                .andExpect(status().isCreated());
        assertThat(notifications(g1.getId(), "NOUVEAU_MESSAGE")).isEqualTo(1);

        postAvec(jetonG1, "/api/messagerie/conversations/" + id + "/messages", Map.of("contenu", "  "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pairesInterditesEtIsolation() throws Exception {
        Utilisateur sa = superAdmin("sa@test.com");
        String jetonSa = jeton("sa@test.com");
        Utilisateur autreAdmin = adminActif("bob@test.com");
        Utilisateur gAutre = gestionnaire("gx@test.com", autreAdmin, true);
        long g2 = utilisateurRepository.findByCourriel("g2@test.com").orElseThrow().getId();

        // Gestionnaire : ni un autre gestionnaire, ni le super admin, ni un autre admin.
        postAvec(jetonG1, "/api/messagerie/conversations", Map.of("interlocuteurId", g2)).andExpect(status().isNotFound());
        postAvec(jetonG1, "/api/messagerie/conversations", Map.of("interlocuteurId", sa.getId()))
                .andExpect(status().isNotFound());
        postAvec(jetonG1, "/api/messagerie/conversations", Map.of("interlocuteurId", autreAdmin.getId()))
                .andExpect(status().isNotFound());
        // Admin : pas le gestionnaire d'une autre entreprise, pas un autre admin.
        postAvec(jetonAdmin, "/api/messagerie/conversations", Map.of("interlocuteurId", gAutre.getId()))
                .andExpect(jsonPath("$.code").value("INTERLOCUTEUR_INTROUVABLE"));
        postAvec(jetonAdmin, "/api/messagerie/conversations", Map.of("interlocuteurId", autreAdmin.getId()))
                .andExpect(status().isNotFound());
        // Super admin : un admin seulement.
        postAvec(jetonSa, "/api/messagerie/conversations", Map.of("interlocuteurId", g1.getId()))
                .andExpect(status().isNotFound());

        // Conversation d'une autre entreprise : invisible.
        long conversationAutre = lire(postAvec(jeton("gx@test.com"), "/api/messagerie/conversations", Map.of()))
                .get("id").asLong();
        getAvec(jetonAdmin, "/api/messagerie/conversations/" + conversationAutre + "/messages")
                .andExpect(status().isNotFound());
        postAvec(jetonG1, "/api/messagerie/conversations/" + conversationAutre + "/messages", Map.of("contenu", "x"))
                .andExpect(status().isNotFound());
        // Le super admin ne voit pas les conversations internes d'une entreprise.
        getAvec(jetonSa, "/api/messagerie/conversations/" + conversationAutre + "/messages")
                .andExpect(status().isNotFound());
        // Un autre gestionnaire de la même entreprise non plus.
        long conversationG1 = lire(postAvec(jetonG1, "/api/messagerie/conversations", Map.of())).get("id").asLong();
        getAvec(jeton("g2@test.com"), "/api/messagerie/conversations/" + conversationG1 + "/messages")
                .andExpect(status().isNotFound());
    }

    @Test
    void conversationAvecBatuloPartageeParLesSuperAdmins() throws Exception {
        Utilisateur sa1 = superAdmin("sa1@test.com");
        Utilisateur sa2 = superAdmin("sa2@test.com");
        long id = lire(postAvec(jetonAdmin, "/api/messagerie/conversations", Map.of())).get("id").asLong();
        assertThat(lire(postAvec(jetonAdmin, "/api/messagerie/conversations", Map.of())).get("id").asLong())
                .isEqualTo(id);
        postAvec(jetonAdmin, "/api/messagerie/conversations/" + id + "/messages", Map.of("contenu", "Bonjour Batulo"))
                .andExpect(status().isCreated());
        assertThat(notifications(sa1.getId(), "NOUVEAU_MESSAGE")).isEqualTo(1);
        assertThat(notifications(sa2.getId(), "NOUVEAU_MESSAGE")).isEqualTo(1);

        String jetonSa2 = jeton("sa2@test.com");
        JsonNode liste = lire(getAvec(jetonSa2, "/api/messagerie/conversations"));
        assertThat(liste.at("/contenu/0/interlocuteur/entreprise").asText()).isEqualTo("Entreprise de awa@test.com");
        postAvec(jetonSa2, "/api/messagerie/conversations/" + id + "/messages", Map.of("contenu", "Bonjour !"))
                .andExpect(jsonPath("$.expediteurNom").value("Prénom Nom (Batulo)"));
        // Le message lu par un super admin l'est pour toute la boîte.
        postAvec(jeton("sa1@test.com"), "/api/messagerie/conversations/" + id + "/lu", Map.of());
        getAvec(jetonSa2, "/api/messagerie/non-lus").andExpect(jsonPath("$.nonLues").value(0));
        JsonNode cote = lire(getAvec(jetonAdmin, "/api/messagerie/conversations"));
        assertThat(cote.at("/contenu/0/interlocuteur/prenom").asText()).isEqualTo("Batulo");
        assertThat(cote.at("/contenu/0/nonLus").asLong()).isEqualTo(1);
    }

    @Test
    void abonnementExpire() throws Exception {
        superAdmin("sa@test.com");
        long avecG1 = lire(postAvec(jetonG1, "/api/messagerie/conversations", Map.of())).get("id").asLong();
        jdbcTemplate.update("update abonnement set date_expiration = ? where entreprise_id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(1))), admin.getEntreprise().getId());
        String jetonExpire = jeton("awa@test.com");

        // Conversation avec un gestionnaire : lecture seule.
        postAvec(jetonExpire, "/api/messagerie/conversations/" + avecG1 + "/messages", Map.of("contenu", "x"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ABONNEMENT_EXPIRE"));
        postAvec(jeton("g1@test.com"), "/api/messagerie/conversations/" + avecG1 + "/messages", Map.of("contenu", "x"))
                .andExpect(status().isForbidden());
        getAvec(jetonExpire, "/api/messagerie/conversations/" + avecG1 + "/messages").andExpect(status().isOk());
        // Avec Batulo : toujours ouverte, et la demande d'augmentation passe.
        long batulo = lire(postAvec(jetonExpire, "/api/messagerie/conversations", Map.of())).get("id").asLong();
        postAvec(jetonExpire, "/api/messagerie/conversations/" + batulo + "/messages", Map.of("contenu", "Aide"))
                .andExpect(status().isCreated());
        postAvec(jetonExpire, "/api/abonnements/demandes", Map.of("formuleSouhaitee", "ANNUEL", "message", "Renouveler"))
                .andExpect(status().isCreated());

        // En attente de paiement : idem.
        admin("neuf@test.com", null);
        String jetonNeuf = jeton("neuf@test.com");
        long batuloNeuf = lire(postAvec(jetonNeuf, "/api/messagerie/conversations", Map.of())
                .andExpect(status().isOk())).get("id").asLong();
        postAvec(jetonNeuf, "/api/messagerie/conversations/" + batuloNeuf + "/messages", Map.of("contenu", "Question"))
                .andExpect(status().isCreated());
        getAvec(jetonNeuf, "/api/sondages").andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- envoi des statistiques

    @Test
    void envoiDesStatistiquesDUnSondageTermine() throws Exception {
        Sondage s = sondageAssigne();
        // Pas encore terminé : 409.
        postAvec(jetonG1, "/api/sondages/" + s.id + "/envois-statistiques", Map.of())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SONDAGE_NON_TERMINE"));
        postAvec(jetonAdmin, "/api/sondages/" + s.id + "/fermer", Map.of()).andExpect(status().isOk());

        // L'admin n'envoie pas (route du gestionnaire) ; un gestionnaire non assigné ne voit pas le sondage.
        postAvec(jetonAdmin, "/api/sondages/" + s.id + "/envois-statistiques", Map.of()).andExpect(status().isForbidden());
        postAvec(jeton("g2@test.com"), "/api/sondages/" + s.id + "/envois-statistiques", Map.of())
                .andExpect(status().isNotFound());
        postAvec(jetonG1, "/api/sondages/" + s.id + "/envois-statistiques",
                Map.of("critere", List.of("inconnue:x"))).andExpect(status().isBadRequest());

        JsonNode envoi = lire(postAvec(jetonG1, "/api/sondages/" + s.id + "/envois-statistiques",
                Map.of("commentaire", "Forte demande à Kaolack", "critere", List.of(s.cleRegion + ":kaolack")))
                .andExpect(status().isCreated()));
        long envoiId = envoi.get("id").asLong();
        assertThat(envoi.get("expediteurNom").asText()).isEqualTo("Moussa Diop");
        assertThat(envoi.get("criteres").get(0).asText()).isEqualTo("Région : Kaolack");

        // Notification dédiée, message automatique dans la conversation (sans « Nouveau message » en double).
        assertThat(notifications(admin.getId(), "STATISTIQUES_RECUES")).isEqualTo(1);
        assertThat(notifications(admin.getId(), "NOUVEAU_MESSAGE")).isZero();
        JsonNode conversation = lire(getAvec(jetonAdmin, "/api/messagerie/conversations")).at("/contenu/0");
        assertThat(conversation.at("/interlocuteur/id").asLong()).isEqualTo(g1.getId());
        JsonNode message = lire(getAvec(jetonAdmin, "/api/messagerie/conversations/" + conversation.get("id").asLong()
                + "/messages")).at("/contenu/0");
        assertThat(message.get("type").asText()).isEqualTo("STATISTIQUES");
        assertThat(message.get("referenceId").asLong()).isEqualTo(envoiId);
        assertThat(message.get("contenu").asText()).contains("Enquête", "version 1", "Région : Kaolack",
                "Commentaire : Forte demande à Kaolack");

        // Côté admin : liste, statistiques recalculées avec le filtre (2 réponses Kaolack sur 3), PDF.
        JsonNode recues = lire(getAvec(jetonAdmin, "/api/statistiques-recues"));
        assertThat(recues.get("totalElements").asLong()).isEqualTo(1);
        assertThat(recues.at("/contenu/0/expediteurNom").asText()).isEqualTo("Moussa Diop");
        assertThat(recues.at("/contenu/0/consulteLe").isNull()).isTrue();
        JsonNode stats = lire(getAvec(jetonAdmin, "/api/statistiques-recues/" + envoiId).andExpect(status().isOk()));
        assertThat(stats.at("/resume/nombreReponses").asLong()).isEqualTo(2);
        assertThat(lire(getAvec(jetonAdmin, "/api/statistiques-recues")).at("/contenu/0/consulteLe").isNull()).isFalse();

        MockHttpServletResponse pdf = getAvec(jetonAdmin, "/api/statistiques-recues/" + envoiId + "/pdf")
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(pdf.getContentType()).isEqualTo("application/pdf");
        PdfReader lecteur = new PdfReader(pdf.getContentAsByteArray());
        assertThat(new PdfTextExtractor(lecteur).getTextFromPage(1)).contains("Enquête", "Région : Kaolack");
        lecteur.close();

        // Côté gestionnaire : ses envois ; il n'a pas accès à la liste de l'admin.
        assertThat(lire(getAvec(jetonG1, "/api/statistiques-envoyees")).get("totalElements").asLong()).isEqualTo(1);
        getAvec(jetonG1, "/api/statistiques-recues").andExpect(status().isForbidden());
        // Un autre admin ne voit pas l'envoi.
        adminActif("bob@test.com");
        getAvec(jeton("bob@test.com"), "/api/statistiques-recues/" + envoiId).andExpect(status().isNotFound());

        // Gestionnaire supprimé : l'envoi reste, avec son nom (GE-06).
        deleteAvec(jetonAdmin, "/api/admin/gestionnaires/" + g1.getId()).andExpect(status().is2xxSuccessful());
        JsonNode apres = lire(getAvec(jetonAdmin, "/api/statistiques-recues")).at("/contenu/0");
        assertThat(apres.get("expediteurNom").asText()).isEqualTo("Moussa Diop");
        assertThat(apres.get("gestionnaireId").isNull()).isTrue();
        getAvec(jetonAdmin, "/api/statistiques-recues/" + envoiId).andExpect(status().isOk());
    }

    @Test
    void envoiAvecAbonnementExpire() throws Exception {
        Sondage s = sondageAssigne();
        postAvec(jetonAdmin, "/api/sondages/" + s.id + "/fermer", Map.of()).andExpect(status().isOk());
        jdbcTemplate.update("update abonnement set date_expiration = ? where entreprise_id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(1))), admin.getEntreprise().getId());
        postAvec(jeton("g1@test.com"), "/api/sondages/" + s.id + "/envois-statistiques", Map.of("lignee", true))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.lignee").value(true));
    }

    // ---------------------------------------------------------------- demandes d'abonnement

    @Test
    void cycleDUneDemandeDAbonnement() throws Exception {
        Utilisateur sa = superAdmin("sa@test.com");
        String jetonSa = jeton("sa@test.com");
        postAvec(jetonAdmin, "/api/abonnements/demandes", Map.of("message", "x")).andExpect(status().isBadRequest());
        long id = lire(postAvec(jetonAdmin, "/api/abonnements/demandes",
                Map.of("formuleSouhaitee", "ANNUEL", "message", "Trois nouvelles enquêtes"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.formuleActuelle").value("MENSUEL"))
                .andExpect(jsonPath("$.statutAbonnement").value("ACTIF"))).get("id").asLong();
        postAvec(jetonAdmin, "/api/abonnements/demandes", Map.of("formuleSouhaitee", "ANNUEL", "message", "Encore"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEMANDE_EN_COURS"));
        assertThat(notifications(sa.getId(), "DEMANDE_ABONNEMENT")).isEqualTo(1);

        // Visible dans la conversation avec Batulo et dans la liste du super admin.
        JsonNode conversation = lire(getAvec(jetonSa, "/api/messagerie/conversations")).at("/contenu/0");
        assertThat(conversation.at("/dernierMessage/type").asText()).isEqualTo("DEMANDE_ABONNEMENT");
        JsonNode liste = lire(getAvec(jetonSa, "/api/super-admin/demandes-abonnement?statut=EN_ATTENTE"));
        assertThat(liste.at("/contenu/0/entreprise").asText()).isEqualTo("Entreprise de awa@test.com");
        getAvec(jetonAdmin, "/api/super-admin/demandes-abonnement").andExpect(status().isForbidden());
        patchJson(jetonAdmin, "/api/super-admin/demandes-abonnement/" + id, Map.of("statut", "TRAITEE"))
                .andExpect(status().isForbidden());

        patchJson(jetonSa, "/api/super-admin/demandes-abonnement/" + id, Map.of("statut", "EN_ATTENTE"))
                .andExpect(status().isBadRequest());
        patchJson(jetonSa, "/api/super-admin/demandes-abonnement/" + id,
                Map.of("statut", "TRAITEE", "reponse", "Choisissez l'annuel depuis la page Abonnement."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("TRAITEE"))
                .andExpect(jsonPath("$.traiteeParNom").value("Prénom Nom"));
        patchJson(jetonSa, "/api/super-admin/demandes-abonnement/" + id, Map.of("statut", "REFUSEE"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEMANDE_DEJA_TRAITEE"));
        assertThat(notifications(admin.getId(), "DEMANDE_ABONNEMENT_TRAITEE")).isEqualTo(1);

        // Aucun effet sur l'abonnement (RG-09).
        assertThat(jdbcTemplate.queryForObject("select formule from abonnement where entreprise_id = ?", String.class,
                admin.getEntreprise().getId())).isEqualTo("MENSUEL");
        JsonNode messages = lire(getAvec(jetonAdmin, "/api/messagerie/conversations/" + conversation.get("id").asLong()
                + "/messages"));
        assertThat(messages.at("/contenu/0/type").asText()).isEqualTo("REPONSE_DEMANDE");
        assertThat(messages.at("/contenu/0/contenu").asText()).contains("Demande acceptée", "page Abonnement");
        // Une nouvelle demande est de nouveau possible ; le gestionnaire ne peut pas demander.
        assertThat(lire(getAvec(jetonAdmin, "/api/abonnements/demandes")).get("totalElements").asLong()).isEqualTo(1);
        postAvec(jetonAdmin, "/api/abonnements/demandes", Map.of("formuleSouhaitee", "MENSUEL", "message", "Autre"))
                .andExpect(status().isCreated());
        postAvec(jetonG1, "/api/abonnements/demandes", Map.of("formuleSouhaitee", "ANNUEL", "message", "x"))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- utilitaires

    private record Sondage(long id, String cleRegion) {
    }

    /** Sondage publié par l'admin, assigné à g1, avec un critère Région et 3 réponses (2 Kaolack, 1 Fatick). */
    private Sondage sondageAssigne() throws Exception {
        long id = lire(postAvec(jetonAdmin, "/api/sondages", Map.of("titre", "Enquête", "reponseUnique", false)))
                .get("id").asLong();
        long section = lire(postAvec(jetonAdmin, "/api/sondages/" + id + "/sections", Map.of("titre", "S")))
                .at("/sections/0/id").asLong();
        postAvec(jetonAdmin, "/api/sondages/" + id + "/sections/" + section + "/questions",
                StatistiqueIntegrationTest.q("Région", "CHOIX_UNIQUE", true, null, "Kaolack", "Fatick"))
                .andExpect(status().isCreated());
        postAvec(jetonAdmin, "/api/sondages/" + id + "/publier", Map.of()).andExpect(status().isOk());
        Map<String, Long> ids = new HashMap<>();
        lire(getAvec(jetonAdmin, "/api/sondages/" + id)).at("/sections/0/questions")
                .forEach(q -> ids.put(q.get("libelle").asText(), q.get("id").asLong()));
        putAvec(jetonAdmin, "/api/sondages/" + id + "/questions/" + ids.get("Région") + "/critere",
                Map.of("estCritere", true)).andExpect(status().isOk());
        postAvec(jetonAdmin, "/api/sondages/" + id + "/assignations", Map.of("gestionnaireIds", List.of(g1.getId())))
                .andExpect(status().isOk());
        String jetonPublic = lire(getAvec(jetonAdmin, "/api/sondages/" + id + "/lien")).get("jeton").asText();
        for (String region : List.of("kaolack", "fatick", "kaolack")) {
            mockMvc.perform(post("/api/public/sondages/" + jetonPublic + "/reponses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("reponses", List.of(Map.of("questionId", ids.get("Région"), "valeur", region))))))
                    .andExpect(status().isCreated());
        }
        String cle = lire(getAvec(jetonAdmin, "/api/sondages/" + id + "/statistiques")).at("/questions/0/cle").asText();
        return new Sondage(id, cle);
    }

    private ResultActions patchJson(String jeton, String url, Object corps) throws Exception {
        return mockMvc.perform(patch(url).header("Authorization", "Bearer " + jeton)
                .contentType(MediaType.APPLICATION_JSON).content(json(corps)));
    }

    private long notifications(Long destinataireId, String type) {
        return jdbcTemplate.queryForObject("select count(*) from notification where destinataire_id = ? and type = ?",
                Long.class, destinataireId, type);
    }

    private JsonNode lire(ResultActions resultat) throws Exception {
        return objectMapper.readTree(resultat.andReturn().getResponse().getContentAsString());
    }
}
