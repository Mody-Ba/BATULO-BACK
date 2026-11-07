package com.batulo.integration;

import com.batulo.entity.Abonnement;
import com.batulo.entity.Entreprise;
import com.batulo.entity.Utilisateur;
import com.batulo.enums.Formule;
import com.batulo.enums.Role;
import com.batulo.enums.StatutAbonnement;
import com.batulo.repository.AbonnementRepository;
import com.batulo.repository.EntrepriseRepository;
import com.batulo.repository.UtilisateurRepository;
import com.batulo.service.email.EmailService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base des tests d'intégration : application complète (sécurité comprise) sur H2, base vidée avant chaque test,
 * comptes créés directement en base. EmailService est un mock : aucun courriel ne part, et les tests peuvent
 * lire le mot de passe temporaire ou le lien de réinitialisation qui aurait été envoyé.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegrationTestBase {

    static final String MOT_DE_PASSE = "MotDePasse123";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    UtilisateurRepository utilisateurRepository;
    @Autowired
    EntrepriseRepository entrepriseRepository;
    @Autowired
    AbonnementRepository abonnementRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    EmailService emailService;

    @BeforeEach
    void viderBase() {
        // Les clés étrangères ON DELETE CASCADE suppriment le reste (abonnements, paiements, jetons, notifications).
        jdbcTemplate.update("delete from entreprise");
        jdbcTemplate.update("delete from utilisateur");
    }

    Utilisateur superAdmin(String courriel) {
        return utilisateurRepository.save(compte(courriel, Role.SUPER_ADMIN, null));
    }

    /** Admin et son entreprise ; expiration null : abonnement en attente de paiement. */
    Utilisateur admin(String courriel, Instant expiration) {
        Entreprise entreprise = new Entreprise();
        entreprise.setNom("Entreprise de " + courriel);
        entreprise.setActif(true);
        entreprise = entrepriseRepository.save(entreprise);

        Abonnement abonnement = new Abonnement();
        abonnement.setEntreprise(entreprise);
        if (expiration == null) {
            abonnement.setStatut(StatutAbonnement.EN_ATTENTE_PAIEMENT);
        } else {
            abonnement.setStatut(StatutAbonnement.ACTIF);
            abonnement.setFormule(Formule.MENSUEL);
            abonnement.setDateDebut(expiration.minus(Duration.ofDays(30)));
            abonnement.setDateExpiration(expiration);
            abonnement.setMontant(new BigDecimal("1.00"));
            abonnement.setDevise("CAD");
        }
        abonnementRepository.save(abonnement);
        return utilisateurRepository.save(compte(courriel, Role.ADMIN, entreprise));
    }

    /** Admin dont l'abonnement est payé pour encore 30 jours. */
    Utilisateur adminActif(String courriel) {
        return admin(courriel, Instant.now().plus(Duration.ofDays(30)));
    }

    Utilisateur gestionnaire(String courriel, Utilisateur admin) {
        return utilisateurRepository.save(compte(courriel, Role.GESTIONNAIRE, admin.getEntreprise()));
    }

    private Utilisateur compte(String courriel, Role role, Entreprise entreprise) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setPrenom("Prénom");
        utilisateur.setNom("Nom");
        utilisateur.setCourriel(courriel);
        utilisateur.setMotDePasse(passwordEncoder.encode(MOT_DE_PASSE));
        utilisateur.setRole(role);
        utilisateur.setEntreprise(entreprise);
        utilisateur.setActif(true);
        return utilisateur;
    }

    /** Se connecte et renvoie toute la réponse (jeton, jetonRafraichissement, role...). */
    JsonNode connexion(String courriel, String motDePasse) throws Exception {
        String reponse = mockMvc.perform(post("/api/auth/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("courriel", courriel, "motDePasse", motDePasse))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(reponse);
    }

    String jeton(String courriel) throws Exception {
        return connexion(courriel, MOT_DE_PASSE).get("jeton").asText();
    }

    ResultActions getAvec(String jeton, String url) throws Exception {
        return mockMvc.perform(avecJeton(get(url), jeton));
    }

    ResultActions postAvec(String jeton, String url, Object corps) throws Exception {
        return mockMvc.perform(avecJeton(post(url), jeton).contentType(MediaType.APPLICATION_JSON).content(json(corps)));
    }

    ResultActions putAvec(String jeton, String url, Object corps) throws Exception {
        return mockMvc.perform(avecJeton(put(url), jeton).contentType(MediaType.APPLICATION_JSON).content(json(corps)));
    }

    ResultActions patchAvec(String jeton, String url) throws Exception {
        return mockMvc.perform(avecJeton(patch(url), jeton));
    }

    ResultActions deleteAvec(String jeton, String url) throws Exception {
        return mockMvc.perform(avecJeton(delete(url), jeton));
    }

    String json(Object objet) throws Exception {
        return objectMapper.writeValueAsString(objet);
    }

    private static MockHttpServletRequestBuilder avecJeton(MockHttpServletRequestBuilder requete, String jeton) {
        return jeton == null ? requete : requete.header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton);
    }
}
