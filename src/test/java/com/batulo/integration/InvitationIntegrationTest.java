package com.batulo.integration;

import com.batulo.entity.Utilisateur;
import com.batulo.enums.Langue;
import com.batulo.service.email.MessageInvitation;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Invitations nominatives : liste (ajout, import CSV, reprise), envoi et relance (courriel simulé, envoi synchrone en
 * test), lien personnel à usage unique, révocation, langue de l'invité, anonymat par défaut et mode nominatif, droits.
 */
class InvitationIntegrationTest extends PostgresIntegrationTestBase {

    private static final ZoneId FUSEAU = ZoneId.of("America/Toronto");

    private Utilisateur admin;
    private String jetonAdmin;

    @BeforeEach
    void preparer() throws Exception {
        admin = adminActif("awa@test.com");
        jetonAdmin = jeton("awa@test.com");
        // Le serveur de courriel accepte tout, sauf l'adresse « refuse@... ».
        when(emailService.envoyerInvitation(anyString(), any())).thenAnswer(a -> !a.<String>getArgument(0).startsWith("refuse@"));
    }

    // ------------------------------------------------------------------ Liste : ajout, import, reprise

    @Test
    void ajoutEtImportCsv() throws Exception {
        long id = brouillon("Enquête", false);
        // Dès le brouillon.
        ajouter(id, "Aminata", "Diallo", "Aminata.Diallo@Exemple.com", "ar").andExpect(status().isCreated())
                .andExpect(jsonPath("$.courriel").value("aminata.diallo@exemple.com"))
                .andExpect(jsonPath("$.langue").value("ar"))
                .andExpect(jsonPath("$.statut").value("A_INVITER"))
                .andExpect(jsonPath("$.jetonHash").doesNotExist())
                .andExpect(jsonPath("$.jetonSel").doesNotExist());
        ajouter(id, null, null, "aminata.diallo@exemple.com", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITE_EXISTANT"));
        ajouter(id, null, null, "pas-un-courriel", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITE_INVALIDE"));
        ajouter(id, null, null, "x@exemple.com", "de").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LANGUE_INVALIDE"));

        String csv = "﻿Prénom;Nom;Courriel;Téléphone;Langue\n"
                + "Moussa;Ba;moussa@exemple.com;+222 22 33 44 55;fr\n"
                + "\"Fatou; dite Fafa\";Sow;fatou@exemple.com;;EN\n"
                + "Sans;Courriel;;;\n"
                + "Mauvais;Courriel;pas-un-courriel;;\n"
                + "Doublon;Fichier;MOUSSA@exemple.com;;\n"
                + "Déjà;Invitée;aminata.diallo@exemple.com;;\n"
                + "Langue;Inconnue;langue@exemple.com;;de\n"
                + "\n"
                + "Omar;Kane;omar@exemple.com;;\n";
        JsonNode rapport = lire(importer(id, csv).andExpect(status().isOk()));
        assertThat(rapport.get("acceptees").asInt()).isEqualTo(3);
        Map<Integer, String> raisons = new LinkedHashMap<>();
        rapport.get("refusees").forEach(r -> raisons.put(r.get("ligne").asInt(), r.get("raison").asText()));
        assertThat(raisons).containsExactly(Map.entry(4, "COURRIEL_MANQUANT"), Map.entry(5, "COURRIEL_INVALIDE"),
                Map.entry(6, "DOUBLON_FICHIER"), Map.entry(7, "DEJA_INVITE"), Map.entry(8, "LANGUE_INVALIDE"));
        JsonNode liste = lire(getAvec(jetonAdmin, url(id, "")).andExpect(status().isOk()));
        assertThat(liste.get("totalElements").asInt()).isEqualTo(4);
        assertThat(liste.at("/contenu/2/prenom").asText()).isEqualTo("Fatou; dite Fafa");
        assertThat(liste.at("/contenu/2/langue").asText()).isEqualTo("en");
        assertThat(liste.at("/contenu/1/telephone").asText()).isEqualTo("+222 22 33 44 55");
        // Recherche sans majuscules ni accents.
        getAvec(jetonAdmin, url(id, "?recherche=MOUSS")).andExpect(jsonPath("$.totalElements").value(1));

        // Séparateur « , » ; en-tête sans courriel ; plus de 1000 lignes.
        importer(id, "courriel,prenom\nzoe@exemple.com,Zoé\n").andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptees").value(1));
        importer(id, "prenom;nom\nA;B\n").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_INVALIDE"));
        StringBuilder gros = new StringBuilder("courriel\n");
        for (int i = 0; i < 1001; i++) {
            gros.append("p").append(i).append("@exemple.com\n");
        }
        importer(id, gros.toString()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_TROP_GRAND"));

        // Modification et suppression avant réponse.
        long moussa = liste.at("/contenu/1/id").asLong();
        putAvec(jetonAdmin, url(id, "/" + moussa), Map.of("prenom", "Moussa", "nom", "Bâ", "courriel",
                "moussa.ba@exemple.com")).andExpect(status().isOk())
                .andExpect(jsonPath("$.courriel").value("moussa.ba@exemple.com"));
        putAvec(jetonAdmin, url(id, "/" + moussa), Map.of("courriel", "fatou@exemple.com"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVITE_EXISTANT"));
        deleteAvec(jetonAdmin, url(id, "/" + moussa)).andExpect(status().isNoContent());
        getAvec(jetonAdmin, url(id, "/resume")).andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.aInviter").value(4));
    }

    // ------------------------------------------------------------------ Envoi, relance, limite

    @Test
    void envoiRelanceDelaiEtLimite() throws Exception {
        long id = brouillon("Satisfaction", false);
        ajouter(id, "Aminata", null, "aminata@exemple.com", "ar").andExpect(status().isCreated());
        ajouter(id, "Omar", null, "omar@exemple.com", null).andExpect(status().isCreated());
        ajouter(id, null, null, "refuse@exemple.com", null).andExpect(status().isCreated());

        // Brouillon : pas d'envoi.
        postAvec(jetonAdmin, url(id, "/envoyer"), Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SONDAGE_NON_OUVERT"));
        verify(emailService, never()).envoyerInvitation(anyString(), any());

        publier(id, Instant.now().plusSeconds(30 * 86400));
        postAvec(jetonAdmin, url(id, "/envoyer"), Map.of()).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.enFile").value(3))
                .andExpect(jsonPath("$.ignorees.length()").value(0));

        // Courriel dans la langue de l'invité, avec le titre traduit, l'entreprise, le lien et la date de fermeture.
        MessageInvitation arabe = message("aminata@exemple.com");
        assertThat(arabe.langue()).isEqualTo(Langue.AR);
        assertThat(arabe.titreSondage()).isEqualTo("عربي Satisfaction");
        assertThat(arabe.entreprise()).isEqualTo("Entreprise de awa@test.com");
        assertThat(arabe.dateFermeture()).isNotNull();
        assertThat(arabe.relance()).isFalse();
        assertThat(arabe.lien()).matches("http://localhost:4200/s/[A-Za-z0-9_-]{43}\\?i=[A-Za-z0-9_-]{43}");
        assertThat(message("omar@exemple.com").langue()).isEqualTo(Langue.FR);
        // Seul le SHA-256 du jeton est en base.
        String jetonOmar = jetonInvitation(message("omar@exemple.com"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from invitation where jeton_hash = ? or jeton_sel = ?",
                Long.class, jetonOmar, jetonOmar)).isZero();

        Map<String, String> statuts = statuts(id);
        assertThat(statuts).containsEntry("aminata@exemple.com", "INVITEE").containsEntry("omar@exemple.com", "INVITEE")
                .containsEntry("refuse@exemple.com", "EN_ECHEC");
        getAvec(jetonAdmin, url(id, "/resume")).andExpect(jsonPath("$.invitees").value(2))
                .andExpect(jsonPath("$.enEchec").value(1)).andExpect(jsonPath("$.envoyees").value(2));

        // « Tous » : seul l'envoi en échec est retenté ; une sélection signale les invitations déjà invitées.
        postAvec(jetonAdmin, url(id, "/envoyer"), Map.of()).andExpect(jsonPath("$.enFile").value(1));
        long omar = idDe(id, "omar@exemple.com");
        postAvec(jetonAdmin, url(id, "/envoyer"), Map.of("invitationIds", List.of(omar, 999999)))
                .andExpect(jsonPath("$.enFile").value(0))
                .andExpect(jsonPath("$.ignorees[0].raison").value("DEJA_INVITEE"))
                .andExpect(jsonPath("$.ignorees[1].raison").value("INTROUVABLE"));

        // Relance : pas avant 24 h, ni pour un invité jamais invité avec succès.
        postAvec(jetonAdmin, url(id, "/relancer"), Map.of()).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.enFile").value(0))
                .andExpect(jsonPath("$.ignorees.length()").value(2))
                .andExpect(jsonPath("$.ignorees[0].raison").value("DELAI_RELANCE"));
        postAvec(jetonAdmin, url(id, "/relancer"), Map.of("invitationIds", List.of(idDe(id, "refuse@exemple.com"))))
                .andExpect(jsonPath("$.ignorees[0].raison").value("JAMAIS_INVITEE"));
        jdbcTemplate.update("update invitation set dernier_envoi_le = dernier_envoi_le - interval '25 hours'");
        clearInvocations(emailService);
        postAvec(jetonAdmin, url(id, "/relancer"), Map.of()).andExpect(jsonPath("$.enFile").value(2));
        assertThat(message("omar@exemple.com").relance()).isTrue();
        // Même lien qu'au premier envoi.
        assertThat(jetonInvitation(message("omar@exemple.com"))).isEqualTo(jetonOmar);
        JsonNode relancee = invitation(id, "omar@exemple.com");
        assertThat(relancee.get("nombreEnvois").asInt()).isEqualTo(2);
        assertThat(relancee.get("derniereRelanceLe").isNull()).isFalse();
        postAvec(jetonAdmin, url(id, "/relancer"), Map.of()).andExpect(jsonPath("$.enFile").value(0));

        // Limite par heure et par entreprise (500 en test) : un lot qui dépasse est refusé en entier.
        jdbcTemplate.update("insert into invitation_envoi (entreprise_id, type, date_envoi, succes) "
                + "select ?, 'INVITATION', now(), true from generate_series(1, 494)", admin.getEntreprise().getId());
        ajouter(id, null, null, "a@exemple.com", null);
        ajouter(id, null, null, "b@exemple.com", null);
        postAvec(jetonAdmin, url(id, "/envoyer"), Map.of()).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LIMITE_ENVOIS"));
        assertThat(statuts(id)).containsEntry("a@exemple.com", "A_INVITER");

        // Export du suivi.
        String export = getAvec(jetonAdmin, url(id, "/export")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(export).startsWith("﻿Prénom;Nom;Courriel;Téléphone;Langue;Statut;")
                .contains("omar@exemple.com", "EN_ECHEC", "INVITEE");
    }

    // ------------------------------------------------------------------ Lien personnel et anonymat

    @Test
    void reponseAvecLienPersonnelAnonymeParDefaut() throws Exception {
        long id = brouillon("Satisfaction", false);
        ajouter(id, "Aminata", "Diallo", "aminata@exemple.com", "ar").andExpect(status().isCreated());
        ajouter(id, null, null, "omar@exemple.com", null).andExpect(status().isCreated());
        publier(id, null);
        postAvec(jetonAdmin, url(id, "/envoyer"), Map.of()).andExpect(status().isAccepted());
        String lien = jetonPublic(id);
        String jetonAminata = jetonInvitation(message("aminata@exemple.com"));
        String jetonOmar = jetonInvitation(message("omar@exemple.com"));

        // Formulaire dans la langue de l'invitée, sans aucune donnée d'identité ; ouverture enregistrée.
        String formulaire = mockMvc.perform(get("/api/public/sondages/" + lien).header("X-Jeton-Invitation", jetonAminata))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.langue").value("ar"))
                .andExpect(jsonPath("$.invitation.dejaRepondu").value(false))
                .andExpect(jsonPath("$.invitation.reponsesNominatives").value(false))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(formulaire).doesNotContain("aminata", "Aminata", "Diallo");
        assertThat(statuts(id)).containsEntry("aminata@exemple.com", "OUVERTE");
        // ?langue= explicite reste prioritaire ; sans lien personnel, pas de bloc invitation.
        mockMvc.perform(get("/api/public/sondages/" + lien + "?langue=fr").header("X-Jeton-Invitation", jetonAminata))
                .andExpect(jsonPath("$.langue").value("fr"));
        mockMvc.perform(get("/api/public/sondages/" + lien)).andExpect(jsonPath("$.invitation").doesNotExist());
        mockMvc.perform(get("/api/public/sondages/" + lien).header("X-Jeton-Invitation", "faux"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INVITATION_INVALIDE"));

        // Une réponse par invitation (sondage à réponse unique : le jeton d'appareil n'est pas exigé).
        envoyer(lien, id, jetonAminata, "ar").andExpect(status().isCreated());
        envoyer(lien, id, jetonAminata, "ar").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEJA_REPONDU"));
        mockMvc.perform(get("/api/public/sondages/" + lien).header("X-Jeton-Invitation", jetonAminata))
                .andExpect(jsonPath("$.invitation.dejaRepondu").value(true));

        // Anonymat : la réponse ne porte aucune trace de l'invitation ; date de réponse arrondie au jour.
        assertThat(jdbcTemplate.queryForObject("select count(*) from reponse where invitation_id is not null",
                Long.class)).isZero();
        Timestamp repondu = jdbcTemplate.queryForObject("select repondu_le from invitation where courriel = ?",
                Timestamp.class, "aminata@exemple.com");
        assertThat(repondu.toInstant().atZone(FUSEAU).toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        String reponses = getAvec(jetonAdmin, "/api/sondages/" + id + "/reponses").andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu[0].invite").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(reponses).doesNotContain("aminata");
        String csv = getAvec(jetonAdmin, "/api/sondages/" + id + "/export/reponses?format=CSV").andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).doesNotContain("aminata", "Invité");
        String stats = getAvec(jetonAdmin, "/api/sondages/" + id + "/statistiques").andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(stats).doesNotContain("aminata");
        // On sait seulement que la personne a répondu.
        assertThat(statuts(id)).containsEntry("aminata@exemple.com", "A_REPONDU");
        getAvec(jetonAdmin, url(id, "/resume")).andExpect(jsonPath("$.repondues").value(1))
                .andExpect(jsonPath("$.tauxReponse").value(50.0));

        // Après la réponse : ni modification, ni suppression, ni révocation.
        long aminata = idDe(id, "aminata@exemple.com");
        deleteAvec(jetonAdmin, url(id, "/" + aminata)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITE_A_REPONDU"));
        putAvec(jetonAdmin, url(id, "/" + aminata), Map.of("courriel", "autre@exemple.com"))
                .andExpect(jsonPath("$.code").value("INVITE_A_REPONDU"));
        postAvec(jetonAdmin, url(id, "/" + aminata + "/revoquer"), Map.of())
                .andExpect(jsonPath("$.code").value("INVITE_A_REPONDU"));

        // Révocation : le lien ne marche plus, même valide.
        long omar = idDe(id, "omar@exemple.com");
        postAvec(jetonAdmin, url(id, "/" + omar + "/revoquer"), Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REVOQUEE"));
        mockMvc.perform(get("/api/public/sondages/" + lien).header("X-Jeton-Invitation", jetonOmar))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("INVITATION_REVOQUEE"));
        envoyer(lien, id, jetonOmar, null).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVITATION_REVOQUEE"));
        postAvec(jetonAdmin, url(id, "/relancer"), Map.of("invitationIds", List.of(omar)))
                .andExpect(jsonPath("$.ignorees[0].raison").value("REVOQUEE"));

        // Le jeton d'un autre sondage ne vaut rien ici.
        long autre = brouillon("Autre", false);
        publier(autre, null);
        mockMvc.perform(get("/api/public/sondages/" + jetonPublic(autre)).header("X-Jeton-Invitation", jetonAminata))
                .andExpect(status().isNotFound());
    }

    @Test
    void modeNominatif() throws Exception {
        long id = brouillon("Nominatif", true);
        getAvec(jetonAdmin, "/api/sondages/" + id).andExpect(jsonPath("$.reponsesNominatives").value(true));
        ajouter(id, "Aminata", "Diallo", "aminata@exemple.com", null).andExpect(status().isCreated());
        publier(id, null);
        // Figé à la publication.
        putAvec(jetonAdmin, "/api/sondages/" + id, Map.of("titre", "Nominatif", "reponsesNominatives", false))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SONDAGE_PUBLIE"));
        postAvec(jetonAdmin, url(id, "/envoyer"), Map.of()).andExpect(status().isAccepted());
        String lien = jetonPublic(id);
        String jeton = jetonInvitation(message("aminata@exemple.com"));

        // Le répondant est prévenu.
        mockMvc.perform(get("/api/public/sondages/" + lien).header("X-Jeton-Invitation", jeton))
                .andExpect(jsonPath("$.invitation.reponsesNominatives").value(true));
        Instant avant = Instant.now();
        envoyer(lien, id, jeton, null).andExpect(status().isCreated());
        // Une réponse par le lien public reste anonyme.
        envoyerPublic(lien, id).andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject("select count(*) from reponse where invitation_id is not null",
                Long.class)).isEqualTo(1);
        Timestamp repondu = jdbcTemplate.queryForObject("select repondu_le from invitation", Timestamp.class);
        assertThat(repondu.toInstant()).isAfterOrEqualTo(avant.minusSeconds(1));
        JsonNode reponses = lire(getAvec(jetonAdmin, "/api/sondages/" + id + "/reponses?taille=10"));
        List<String> invites = new ArrayList<>();
        reponses.get("contenu").forEach(r -> invites.add(r.path("invite").path("courriel").asText("")));
        assertThat(invites).containsExactlyInAnyOrder("aminata@exemple.com", "");

        String csv = getAvec(jetonAdmin, "/api/sondages/" + id + "/export/reponses?format=CSV").andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        String[] lignes = csv.substring(1).split("\r\n");
        assertThat(lignes[0]).isEqualTo("Id;Date;Version;Langue;Invité;Courriel de l'invité;Q");
        assertThat(csv).contains(";fr;Aminata Diallo;aminata@exemple.com;Oui");
    }

    // ------------------------------------------------------------------ Versions, copies, reprise

    @Test
    void nouvelleVersionPartageLaListeEtRepriseApresDuplication() throws Exception {
        long v1 = brouillon("Source", false);
        ajouter(v1, null, null, "aminata@exemple.com", null).andExpect(status().isCreated());
        ajouter(v1, null, null, "omar@exemple.com", "en").andExpect(status().isCreated());
        publier(v1, null);
        postAvec(jetonAdmin, url(v1, "/envoyer"), Map.of()).andExpect(status().isAccepted());
        String lien = jetonPublic(v1);
        String jeton = jetonInvitation(message("aminata@exemple.com"));
        envoyer(lien, v1, jeton, null).andExpect(status().isCreated());

        // Nouvelle version : même liste (rien n'est copié), même lien ; une seule réponse par invité sur la lignée.
        long v2 = lire(postAvec(jetonAdmin, "/api/sondages/" + v1 + "/nouvelle-version", Map.of())
                .andExpect(status().isCreated())).get("id").asLong();
        getAvec(jetonAdmin, url(v2, "")).andExpect(jsonPath("$.totalElements").value(2));
        publier(v2, null);
        envoyer(lien, v2, jeton, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEJA_REPONDU"));
        String jetonOmar = jetonInvitation(message("omar@exemple.com"));
        envoyer(lien, v2, jetonOmar, null).andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject("select count(*) from invitation", Long.class)).isEqualTo(2);

        // Duplication : aucun invité ; la liste se reprend (identité seulement), sans doublon.
        long copie = lire(postAvec(jetonAdmin, "/api/sondages/" + v1 + "/dupliquer", Map.of())
                .andExpect(status().isCreated())).get("id").asLong();
        getAvec(jetonAdmin, url(copie, "")).andExpect(jsonPath("$.totalElements").value(0));
        postAvec(jetonAdmin, url(copie, "/reprendre"), Map.of("sondageSourceId", v2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptees").value(2));
        postAvec(jetonAdmin, url(copie, "/reprendre"), Map.of("sondageSourceId", v1))
                .andExpect(jsonPath("$.acceptees").value(0))
                .andExpect(jsonPath("$.refusees[0].raison").value("DEJA_INVITE"));
        JsonNode reprise = lire(getAvec(jetonAdmin, url(copie, "")));
        assertThat(reprise.at("/contenu/0/statut").asText()).isEqualTo("A_INVITER");
        assertThat(reprise.at("/contenu/1/langue").asText()).isEqualTo("en");
    }

    // ------------------------------------------------------------------ Droits

    @Test
    void accesReserveALEntrepriseEtAuxGestionnairesAssignes() throws Exception {
        long id = brouillon("Privé", false);
        ajouter(id, null, null, "aminata@exemple.com", null).andExpect(status().isCreated());

        adminActif("bob@test.com");
        String bob = jeton("bob@test.com");
        getAvec(bob, url(id, "")).andExpect(status().isNotFound());
        postAvec(bob, url(id, ""), Map.of("courriel", "x@exemple.com")).andExpect(status().isNotFound());
        getAvec(bob, url(id, "/export")).andExpect(status().isNotFound());

        Long g1Id = gestionnaire("g1@test.com", admin, true).getId();
        String g1 = jeton("g1@test.com");
        getAvec(g1, url(id, "")).andExpect(status().isNotFound());
        postAvec(g1, url(id, "/envoyer"), Map.of()).andExpect(status().isNotFound());

        postAvec(jetonAdmin, "/api/sondages/" + id + "/assignations", Map.of("gestionnaireIds", List.of(g1Id)))
                .andExpect(status().is2xxSuccessful());
        getAvec(g1, url(id, "")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        postAvec(g1, url(id, ""), Map.of("courriel", "omar@exemple.com")).andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ Outils

    /** Brouillon avec une question OUI_NON « Q », langues fr + ar (traduites), réponse unique par appareil. */
    private long brouillon(String titre, boolean nominatif) throws Exception {
        long id = lire(postAvec(jetonAdmin, "/api/sondages", Map.of("titre", titre)).andExpect(status().isCreated()))
                .get("id").asLong();
        if (nominatif) {
            putAvec(jetonAdmin, "/api/sondages/" + id, Map.of("titre", titre, "reponsesNominatives", true))
                    .andExpect(status().isOk());
        }
        long section = lire(postAvec(jetonAdmin, "/api/sondages/" + id + "/sections", Map.of("titre", "S")))
                .at("/sections/0/id").asLong();
        postAvec(jetonAdmin, "/api/sondages/" + id + "/sections/" + section + "/questions",
                SondageIntegrationTest.question("Q", "OUI_NON", null)).andExpect(status().isCreated());
        putAvec(jetonAdmin, "/api/sondages/" + id + "/langues", Map.of("languePrincipale", "fr",
                "languesSupplementaires", List.of("ar"))).andExpect(status().isOk());
        JsonNode textes = lire(getAvec(jetonAdmin, "/api/sondages/" + id + "/traductions/ar")).get("textes");
        List<Map<String, Object>> traductions = new ArrayList<>();
        textes.forEach(t -> traductions.add(Map.of("cle", t.get("cle").asText(),
                "traduction", "عربي " + t.get("source").asText())));
        putAvec(jetonAdmin, "/api/sondages/" + id + "/traductions/ar", Map.of("textes", traductions))
                .andExpect(status().isOk());
        return id;
    }

    private void publier(long id, Instant fermeture) throws Exception {
        Map<String, Object> dates = new HashMap<>();
        if (fermeture != null) {
            dates.put("dateFermeture", fermeture.toString());
        }
        postAvec(jetonAdmin, "/api/sondages/" + id + "/publier", dates).andExpect(status().isOk());
    }

    private static String url(long sondageId, String suite) {
        return "/api/sondages/" + sondageId + "/invitations" + suite;
    }

    private ResultActions ajouter(long id, String prenom, String nom, String courriel, String langue) throws Exception {
        Map<String, Object> corps = new HashMap<>();
        corps.put("prenom", prenom);
        corps.put("nom", nom);
        corps.put("courriel", courriel);
        corps.put("langue", langue);
        return postAvec(jetonAdmin, url(id, ""), corps);
    }

    private ResultActions importer(long id, String contenu) throws Exception {
        MockMultipartFile fichier = new MockMultipartFile("fichier", "invites.csv", "text/csv",
                contenu.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart(url(id, "/import")).file(fichier)
                .header("Authorization", "Bearer " + jetonAdmin));
    }

    /** Dernier courriel d'invitation envoyé à cette adresse. */
    private MessageInvitation message(String courriel) {
        ArgumentCaptor<MessageInvitation> capture = ArgumentCaptor.forClass(MessageInvitation.class);
        verify(emailService, atLeastOnce()).envoyerInvitation(eq(courriel), capture.capture());
        return capture.getValue();
    }

    private static String jetonInvitation(MessageInvitation message) {
        return message.lien().substring(message.lien().indexOf("?i=") + 3);
    }

    private String jetonPublic(long id) throws Exception {
        return lire(getAvec(jetonAdmin, "/api/sondages/" + id + "/lien").andExpect(status().isOk())).get("jeton").asText();
    }

    private Map<String, String> statuts(long id) throws Exception {
        Map<String, String> statuts = new HashMap<>();
        lire(getAvec(jetonAdmin, url(id, "?taille=100"))).get("contenu")
                .forEach(i -> statuts.put(i.get("courriel").asText(), i.get("statut").asText()));
        return statuts;
    }

    private JsonNode invitation(long id, String courriel) throws Exception {
        for (JsonNode i : lire(getAvec(jetonAdmin, url(id, "?taille=100"))).get("contenu")) {
            if (i.get("courriel").asText().equals(courriel)) {
                return i;
            }
        }
        throw new AssertionError("Invité introuvable : " + courriel);
    }

    private long idDe(long id, String courriel) throws Exception {
        return invitation(id, courriel).get("id").asLong();
    }

    private ResultActions envoyer(String lien, long sondageId, String jetonInvitation, String langue) throws Exception {
        Map<String, Object> corps = new HashMap<>();
        corps.put("jetonInvitation", jetonInvitation);
        if (langue != null) {
            corps.put("langue", langue);
        }
        corps.put("reponses", List.of(Map.of("questionId", questionId(sondageId), "valeur", true)));
        return mockMvc.perform(post("/api/public/sondages/" + lien + "/reponses")
                .contentType(MediaType.APPLICATION_JSON).content(json(corps)));
    }

    private ResultActions envoyerPublic(String lien, long sondageId) throws Exception {
        Map<String, Object> corps = Map.of("jetonRepondant", "3f6c2a1e-8b1d-4c3e-9a77-2f0e5b4d9c11",
                "reponses", List.of(Map.of("questionId", questionId(sondageId), "valeur", false)));
        return mockMvc.perform(post("/api/public/sondages/" + lien + "/reponses")
                .contentType(MediaType.APPLICATION_JSON).content(json(corps)));
    }

    private long questionId(long sondageId) throws Exception {
        return lire(getAvec(jetonAdmin, "/api/sondages/" + sondageId)).at("/sections/0/questions/0/id").asLong();
    }

    private JsonNode lire(ResultActions resultat) throws Exception {
        return objectMapper.readTree(resultat.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
