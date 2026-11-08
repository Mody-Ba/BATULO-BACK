package com.batulo.service;

import com.batulo.entity.OptionQuestion;
import com.batulo.entity.Question;
import com.batulo.entity.Reponse;
import com.batulo.entity.ReponseQuestion;
import com.batulo.entity.Sondage;
import com.batulo.enums.StatutSondage;
import com.batulo.exception.ConflitException;
import com.batulo.repository.ReponseRepository;
import com.batulo.service.model.SoumissionModel.ValeurSoumise;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.SplittableRandom;

/**
 * N'existe qu'avec le profil dev. Chaque réponse générée passe par {@link ValidateurReponse}, comme une vraie :
 * elle est donc forcément valide. Pas de jeton d'appareil (elles ne bloquent aucun répondant).
 */
@Service
@Profile("dev")
public class DevReponsesServiceImpl implements DevReponsesService {

    private static final Logger log = LoggerFactory.getLogger(DevReponsesServiceImpl.class);
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final Duration ETALEMENT = Duration.ofDays(30);
    private static final List<String> TEXTES = List.of(
            "Manque d'eau pendant la saison sèche.", "Le prix des semences a augmenté.", "Très satisfait du suivi.",
            "Les routes sont difficiles en hivernage.", "Besoin de formation sur les engrais.", "RAS",
            "Accès au crédit compliqué.", "Bonne récolte cette année.");

    private final AccesSondage accesSondage;
    private final ValidateurReponse validateurReponse;
    private final ReponseRepository reponseRepository;
    private final SplittableRandom aleatoire = new SplittableRandom();

    public DevReponsesServiceImpl(AccesSondage accesSondage, ValidateurReponse validateurReponse,
                                  ReponseRepository reponseRepository) {
        this.accesSondage = accesSondage;
        this.validateurReponse = validateurReponse;
        this.reponseRepository = reponseRepository;
    }

    @Override
    @Transactional
    public int genererReponses(Long utilisateurId, Long sondageId, int nombre) {
        Sondage sondage = accesSondage.sondageLisible(accesSondage.acteur(utilisateurId), sondageId);
        Instant maintenant = Instant.now();
        if (RegleSondage.statut(sondage, maintenant) != StatutSondage.EN_COURS) {
            throw new ConflitException("SONDAGE_NON_EN_COURS",
                    "Les réponses de test ne se génèrent que sur un sondage EN_COURS.");
        }
        List<Question> questions = sondage.getSections().stream().flatMap(s -> s.getQuestions().stream()).toList();
        List<Reponse> reponses = new ArrayList<>();
        for (int i = 0; i < nombre; i++) {
            List<ValeurSoumise> valeurs = new ArrayList<>();
            for (Question question : questions) {
                // Facultative : répondue 8 fois sur 10.
                if (question.isObligatoire() || aleatoire.nextInt(10) < 8) {
                    valeurs.add(new ValeurSoumise(question.getId(), valeur(question)));
                }
            }
            Reponse reponse = new Reponse();
            reponse.setSondage(sondage);
            reponse.setLignee(sondage.getRacine());
            reponse.setDateSoumission(maintenant.minusSeconds(aleatoire.nextLong(ETALEMENT.toSeconds())));
            for (ReponseQuestion ligne : validateurReponse.valider(questions, valeurs)) {
                ligne.setReponse(reponse);
                reponse.getValeurs().add(ligne);
            }
            reponses.add(reponse);
        }
        reponseRepository.saveAll(reponses);
        log.info("[DEV] {} réponses de test générées pour le sondage {}", nombre, sondageId);
        return nombre;
    }

    private JsonNode valeur(Question question) {
        JsonNode config = question.getConfiguration();
        List<String> options = question.getOptions().stream().map(OptionQuestion::getValeur).toList();
        return switch (question.getType()) {
            case CHOIX_UNIQUE -> JSON.textNode(options.get(aleatoire.nextInt(options.size())));
            case CHOIX_MULTIPLE -> {
                List<String> melangees = melanger(options);
                ArrayNode choix = JSON.arrayNode();
                melangees.subList(0, 1 + aleatoire.nextInt(melangees.size())).forEach(choix::add);
                yield choix;
            }
            case OUI_NON -> JSON.booleanNode(aleatoire.nextBoolean());
            case TEXTE_COURT, TEXTE_LONG -> {
                String texte = TEXTES.get(aleatoire.nextInt(TEXTES.size()));
                int max = config.path("longueurMax").asInt(texte.length());
                yield JSON.textNode(texte.length() > max ? texte.substring(0, max) : texte);
            }
            case ECHELLE -> JSON.numberNode(aleatoire.nextInt(config.path("min").asInt(1), config.path("max").asInt(5) + 1));
            case NUMERIQUE -> {
                double min = config.hasNonNull("min") ? config.get("min").asDouble() : 0;
                double max = config.hasNonNull("max") ? config.get("max").asDouble() : min + 100;
                int decimales = config.path("decimales").asInt(0);
                // Bornes arrondies vers l'intérieur : la valeur reste dans [min, max] avec le bon nombre de décimales.
                BigDecimal bas = BigDecimal.valueOf(min).setScale(decimales, RoundingMode.CEILING);
                BigDecimal haut = BigDecimal.valueOf(max).setScale(decimales, RoundingMode.FLOOR);
                BigDecimal valeur = BigDecimal.valueOf(min + aleatoire.nextDouble() * (max - min))
                        .setScale(decimales, RoundingMode.DOWN);
                yield JSON.numberNode(valeur.max(bas).min(haut));
            }
            case DATE -> {
                LocalDate fin = config.hasNonNull("dateMax") ? LocalDate.parse(config.get("dateMax").asText()) : LocalDate.now();
                LocalDate debut = config.hasNonNull("dateMin") ? LocalDate.parse(config.get("dateMin").asText()) : fin.minusYears(1);
                long jours = Math.max(0, fin.toEpochDay() - debut.toEpochDay());
                yield JSON.textNode(debut.plusDays(aleatoire.nextLong(jours + 1)).toString());
            }
            case CLASSEMENT -> {
                ArrayNode ordre = JSON.arrayNode();
                melanger(options).forEach(ordre::add);
                yield ordre;
            }
            case MATRICE -> {
                List<String> colonnes = new ArrayList<>();
                config.path("colonnes").forEach(c -> colonnes.add(c.asText()));
                ObjectNode matrice = JSON.objectNode();
                config.path("lignes").forEach(l -> matrice.put(l.asText(), colonnes.get(aleatoire.nextInt(colonnes.size()))));
                yield matrice;
            }
        };
    }

    private List<String> melanger(List<String> valeurs) {
        List<String> copie = new ArrayList<>(valeurs);
        Collections.shuffle(copie, new Random(aleatoire.nextLong()));
        return copie;
    }
}
