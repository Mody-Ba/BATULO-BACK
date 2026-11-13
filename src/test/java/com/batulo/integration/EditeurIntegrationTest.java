package com.batulo.integration;

import com.batulo.entity.Utilisateur;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ajouts pour l'éditeur : nombre de réponses par version, déplacement d'une question entre sections. */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class EditeurIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private Utilisateur admin;
    private String jetonAdmin;

    @BeforeEach
    void preparer() throws Exception {
        admin = adminActif("awa@test.com");
        jetonAdmin = jeton("awa@test.com");
    }

    // ------------------------------------------------------------------ Versions : nombre de réponses

    @Test
    void versionsDonnentLeurNombreDeReponses() throws Exception {
        long v1 = sondagePublie("Satisfaction");
        repondre(v1, v1, 3);
        long v2 = nouvelleVersion(v1);
        postAvec(jetonAdmin, "/api/sondages/" + v2 + "/publier", Map.of()).andExpect(status().isOk());
        repondre(v2, v1, 1);
        long v3 = nouvelleVersion(v2);

        JsonNode versions = lire(getAvec(jetonAdmin, "/api/sondages/" + v3 + "/versions").andExpect(status().isOk()));
        assertThat(versions).hasSize(3);
        assertThat(versions.at("/0/id").asLong()).isEqualTo(v1);
        assertThat(versions.at("/0/nombreReponses").asLong()).isEqualTo(3);
        assertThat(versions.at("/1/nombreReponses").asLong()).isEqualTo(1);
        assertThat(versions.at("/2/nombreReponses").asLong()).isZero();

        // Le gestionnaire assigné voit les mêmes nombres.
        Utilisateur g = gestionnaire("g@test.com", admin);
        postAvec(jetonAdmin, "/api/sondages/" + v1 + "/assignations", Map.of("gestionnaireIds", List.of(g.getId())))
                .andExpect(status().isOk());
        getAvec(jeton("g@test.com"), "/api/sondages/" + v1 + "/versions")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombreReponses").value(3))
                .andExpect(jsonPath("$[1].nombreReponses").value(1));
    }

    @Test
    void versionsSansRequeteParVersion() throws Exception {
        long v1 = sondagePublie("Une version");
        repondre(v1, v1, 2);
        long uneVersion = requetesPour("/api/sondages/" + v1 + "/versions");

        long v2 = nouvelleVersion(v1);
        postAvec(jetonAdmin, "/api/sondages/" + v2 + "/publier", Map.of()).andExpect(status().isOk());
        repondre(v2, v1, 2);
        long v3 = nouvelleVersion(v2);
        assertThat(requetesPour("/api/sondages/" + v3 + "/versions")).isEqualTo(uneVersion);
    }

    // ------------------------------------------------------------------ Déplacement d'une question

    @Test
    void deplacerVersUneAutreSectionRenumeroteLesDeux() throws Exception {
        Brouillon b = brouillon();
        // Q1 au milieu de B (Q4, Q1, Q5).
        JsonNode sondage = lire(deplacer(b.id, b.q("Q1"), b.sectionB, 2).andExpect(status().isOk()));
        assertThat(libelles(sondage, 0)).containsExactly("Q2", "Q3");
        assertThat(libelles(sondage, 1)).containsExactly("Q4", "Q1", "Q5");
        assertThat(ordres(sondage, 0)).containsExactly(1, 2);
        assertThat(ordres(sondage, 1)).containsExactly(1, 2, 3);

        // La question garde son id, ses options (mêmes valeurs), sa clé et son critère.
        JsonNode q1 = sondage.at("/sections/1/questions/1");
        assertThat(q1.get("id").asLong()).isEqualTo(b.q("Q1"));
        assertThat(q1.get("type").asText()).isEqualTo("CHOIX_UNIQUE");
        assertThat(q1.get("estCritere").asBoolean()).isTrue();
        List<String> valeurs = new ArrayList<>();
        q1.get("options").forEach(o -> valeurs.add(o.get("valeur").asText()));
        assertThat(valeurs).isEqualTo(b.valeursQ1);
        assertThat(cle(b.q("Q1"))).isEqualTo(b.cleQ1);
        assertThat(jdbcTemplate.queryForObject("select section_id from question where id = ?", Long.class, b.q("Q1")))
                .isEqualTo(b.sectionB);

        // Au début, puis à la fin sans position.
        sondage = lire(deplacer(b.id, b.q("Q3"), b.sectionB, 1).andExpect(status().isOk()));
        assertThat(libelles(sondage, 0)).containsExactly("Q2");
        assertThat(libelles(sondage, 1)).containsExactly("Q3", "Q4", "Q1", "Q5");
        sondage = lire(putAvec(jetonAdmin, "/api/sondages/" + b.id + "/questions/" + b.q("Q4") + "/section",
                Map.of("sectionId", b.sectionA)).andExpect(status().isOk()));
        assertThat(libelles(sondage, 0)).containsExactly("Q2", "Q4");
        assertThat(libelles(sondage, 1)).containsExactly("Q3", "Q1", "Q5");
        assertThat(ordres(sondage, 1)).containsExactly(1, 2, 3);

        // Les deux dernières : la section de départ reste vide ; le contenu relu en base est le même.
        deplacer(b.id, b.q("Q2"), b.sectionB, 4).andExpect(status().isOk());
        deplacer(b.id, b.q("Q4"), b.sectionB, 2).andExpect(status().isOk());
        JsonNode relu = lire(getAvec(jetonAdmin, "/api/sondages/" + b.id));
        assertThat(libelles(relu, 0)).isEmpty();
        assertThat(libelles(relu, 1)).containsExactly("Q3", "Q4", "Q1", "Q5", "Q2");
        assertThat(ordres(relu, 1)).containsExactly(1, 2, 3, 4, 5);
        assertThat(jdbcTemplate.queryForObject("select count(*) from question q join section s on s.id = q.section_id "
                + "where s.sondage_id = ?", Long.class, b.id)).isEqualTo(5);
    }

    @Test
    void repositionnerDansLaMemeSection() throws Exception {
        Brouillon b = brouillon();
        JsonNode sondage = lire(deplacer(b.id, b.q("Q1"), b.sectionA, 3).andExpect(status().isOk()));
        assertThat(libelles(sondage, 0)).containsExactly("Q2", "Q3", "Q1");
        assertThat(ordres(sondage, 0)).containsExactly(1, 2, 3);
        sondage = lire(deplacer(b.id, b.q("Q1"), b.sectionA, 1).andExpect(status().isOk()));
        assertThat(libelles(sondage, 0)).containsExactly("Q1", "Q2", "Q3");
        // 3 questions dans la section : position 4 refusée (la question compte déjà).
        deplacer(b.id, b.q("Q1"), b.sectionA, 4)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POSITION_INVALIDE"));
    }

    @Test
    void erreursDeDeplacement() throws Exception {
        Brouillon b = brouillon();
        // B a 2 questions : positions 1 à 3.
        deplacer(b.id, b.q("Q1"), b.sectionB, 4)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("POSITION_INVALIDE"));
        deplacer(b.id, b.q("Q1"), b.sectionB, 0).andExpect(status().isBadRequest());
        putAvec(jetonAdmin, "/api/sondages/" + b.id + "/questions/" + b.q("Q1") + "/section", Map.of())
                .andExpect(status().isBadRequest());

        // Section ou question d'un autre sondage.
        Brouillon autre = brouillon();
        deplacer(b.id, b.q("Q1"), autre.sectionA, 1)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SECTION_INTROUVABLE"));
        deplacer(b.id, autre.q("Q1"), b.sectionB, 1)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("QUESTION_INTROUVABLE"));

        // Rien n'a bougé.
        JsonNode relu = lire(getAvec(jetonAdmin, "/api/sondages/" + b.id));
        assertThat(libelles(relu, 0)).containsExactly("Q1", "Q2", "Q3");
        assertThat(libelles(relu, 1)).containsExactly("Q4", "Q5");

        // Publié : gelé.
        postAvec(jetonAdmin, "/api/sondages/" + b.id + "/publier", Map.of()).andExpect(status().isOk());
        deplacer(b.id, b.q("Q1"), b.sectionB, 1)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SONDAGE_PUBLIE"));
    }

    @Test
    void sectionPleine() throws Exception {
        Brouillon b = brouillon();
        for (int i = 2; i < 100; i++) {
            postAvec(jetonAdmin, "/api/sondages/" + b.id + "/sections/" + b.sectionB + "/questions",
                    SondageIntegrationTest.question("R" + i, "OUI_NON", null)).andExpect(status().isCreated());
        }
        deplacer(b.id, b.q("Q1"), b.sectionB, 1)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LIMITE_ATTEINTE"));
        // Repositionner dans une section pleine reste possible.
        deplacer(b.id, b.q("Q4"), b.sectionB, 100).andExpect(status().isOk());
    }

    @Test
    void memesDroitsQueLaModificationDUneQuestion() throws Exception {
        Utilisateur createur = gestionnaire("createur@test.com", admin, true);
        Utilisateur assigne = gestionnaire("assigne@test.com", admin, true);
        String jetonCreateur = jeton("createur@test.com");
        Brouillon b = brouillon(jetonCreateur);
        postAvec(jetonAdmin, "/api/sondages/" + b.id + "/assignations",
                Map.of("gestionnaireIds", List.of(assigne.getId()))).andExpect(status().isOk());

        // L'assigné voit le sondage mais ne le modifie pas.
        putAvec(jeton("assigne@test.com"), "/api/sondages/" + b.id + "/questions/" + b.q("Q1") + "/section",
                Map.of("sectionId", b.sectionB))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACTION_RESERVEE_CREATEUR"));

        // Le créateur sans la permission non plus.
        createur.setPeutCreerSondages(false);
        utilisateurRepository.save(createur);
        putAvec(jetonCreateur, "/api/sondages/" + b.id + "/questions/" + b.q("Q1") + "/section",
                Map.of("sectionId", b.sectionB))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_SONDAGES_REQUISE"));

        // L'admin de l'entreprise, oui (comme pour la modification d'une question).
        deplacer(b.id, b.q("Q1"), b.sectionB, 1).andExpect(status().isOk());

        // Une autre entreprise ne le voit pas.
        adminActif("autre@test.com");
        putAvec(jeton("autre@test.com"), "/api/sondages/" + b.id + "/questions/" + b.q("Q1") + "/section",
                Map.of("sectionId", b.sectionA)).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ Outils

    /** Brouillon : section A (Q1 choix unique critère, Q2, Q3), section B (Q4, Q5). */
    private record Brouillon(long id, long sectionA, long sectionB, Map<String, Long> questions,
                             List<String> valeursQ1, String cleQ1) {
        long q(String libelle) {
            return questions.get(libelle);
        }
    }

    private Brouillon brouillon() throws Exception {
        return brouillon(jetonAdmin);
    }

    private Brouillon brouillon(String jeton) throws Exception {
        long id = lire(postAvec(jeton, "/api/sondages", Map.of("titre", "Éditeur")).andExpect(status().isCreated()))
                .get("id").asLong();
        long sectionA = lire(postAvec(jeton, "/api/sondages/" + id + "/sections", Map.of("titre", "A")))
                .at("/sections/0/id").asLong();
        long sectionB = lire(postAvec(jeton, "/api/sondages/" + id + "/sections", Map.of("titre", "B")))
                .at("/sections/1/id").asLong();
        postAvec(jeton, "/api/sondages/" + id + "/sections/" + sectionA + "/questions",
                SondageIntegrationTest.question("Q1", "CHOIX_UNIQUE", null, "Oui", "Non", "Peut-être"))
                .andExpect(status().isCreated());
        for (String libelle : List.of("Q2", "Q3")) {
            postAvec(jeton, "/api/sondages/" + id + "/sections/" + sectionA + "/questions",
                    SondageIntegrationTest.question(libelle, "OUI_NON", null)).andExpect(status().isCreated());
        }
        JsonNode sondage = null;
        for (String libelle : List.of("Q4", "Q5")) {
            sondage = lire(postAvec(jeton, "/api/sondages/" + id + "/sections/" + sectionB + "/questions",
                    SondageIntegrationTest.question(libelle, "OUI_NON", null)).andExpect(status().isCreated()));
        }
        Map<String, Long> questions = new HashMap<>();
        sondage.get("sections").forEach(s -> s.get("questions")
                .forEach(q -> questions.put(q.get("libelle").asText(), q.get("id").asLong())));
        putAvec(jeton, "/api/sondages/" + id + "/questions/" + questions.get("Q1") + "/critere",
                Map.of("estCritere", true)).andExpect(status().isOk());
        List<String> valeurs = new ArrayList<>();
        sondage.at("/sections/0/questions/0/options").forEach(o -> valeurs.add(o.get("valeur").asText()));
        return new Brouillon(id, sectionA, sectionB, questions, valeurs, cle(questions.get("Q1")));
    }

    private ResultActions deplacer(long sondageId, long questionId, long sectionId, int position) throws Exception {
        return putAvec(jetonAdmin, "/api/sondages/" + sondageId + "/questions/" + questionId + "/section",
                Map.of("sectionId", sectionId, "position", position));
    }

    private String cle(long questionId) {
        return jdbcTemplate.queryForObject("select cast(cle as varchar) from question where id = ?", String.class,
                questionId);
    }

    private static List<String> libelles(JsonNode sondage, int section) {
        List<String> libelles = new ArrayList<>();
        sondage.at("/sections/" + section + "/questions").forEach(q -> libelles.add(q.get("libelle").asText()));
        return libelles;
    }

    private static List<Integer> ordres(JsonNode sondage, int section) {
        List<Integer> ordres = new ArrayList<>();
        sondage.at("/sections/" + section + "/questions").forEach(q -> ordres.add(q.get("ordre").asInt()));
        return ordres;
    }

    private long sondagePublie(String titre) throws Exception {
        long id = lire(postAvec(jetonAdmin, "/api/sondages", Map.of("titre", titre)).andExpect(status().isCreated()))
                .get("id").asLong();
        long sectionId = lire(postAvec(jetonAdmin, "/api/sondages/" + id + "/sections", Map.of("titre", "S")))
                .at("/sections/0/id").asLong();
        postAvec(jetonAdmin, "/api/sondages/" + id + "/sections/" + sectionId + "/questions",
                SondageIntegrationTest.question("Q", "OUI_NON", null)).andExpect(status().isCreated());
        postAvec(jetonAdmin, "/api/sondages/" + id + "/publier", Map.of()).andExpect(status().isOk());
        return id;
    }

    private long nouvelleVersion(long sondageId) throws Exception {
        return lire(postAvec(jetonAdmin, "/api/sondages/" + sondageId + "/nouvelle-version", Map.of())
                .andExpect(status().isCreated())).get("id").asLong();
    }

    /** Réponses vides insérées directement : seuls les comptages sont testés ici. */
    private void repondre(long sondageId, long ligneeId, int nombre) {
        for (int i = 0; i < nombre; i++) {
            jdbcTemplate.update("insert into reponse (sondage_id, lignee_id, date_soumission) values (?, ?, ?)",
                    sondageId, ligneeId, Timestamp.from(Instant.now()));
        }
    }

    private long requetesPour(String url) throws Exception {
        Statistics statistiques = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistiques.clear();
        getAvec(jetonAdmin, url).andExpect(status().isOk());
        return statistiques.getPrepareStatementCount();
    }

    private JsonNode lire(ResultActions resultat) throws Exception {
        return objectMapper.readTree(resultat.andReturn().getResponse().getContentAsString());
    }
}
