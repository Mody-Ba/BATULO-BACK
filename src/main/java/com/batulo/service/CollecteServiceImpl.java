package com.batulo.service;

import com.batulo.entity.Question;
import com.batulo.entity.Reponse;
import com.batulo.entity.ReponseQuestion;
import com.batulo.entity.Sondage;
import com.batulo.enums.StatutSondage;
import com.batulo.exception.ActionInterditeException;
import com.batulo.exception.ConflitException;
import com.batulo.exception.RegleGestionException;
import com.batulo.exception.ResourceNotFoundException;
import com.batulo.exception.RessourceFermeeException;
import com.batulo.repository.ReponseRepository;
import com.batulo.repository.SondageRepository;
import com.batulo.service.mappers.SondageMapper;
import com.batulo.service.model.FormulairePublicModel;
import com.batulo.service.model.SoumissionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CollecteServiceImpl implements CollecteService {

    private static final Logger log = LoggerFactory.getLogger(CollecteServiceImpl.class);

    private static final String MESSAGE_FERME = "Ce sondage n'accepte plus de réponses.";

    private final SondageRepository sondageRepository;
    private final ReponseRepository reponseRepository;
    private final SondageMapper sondageMapper;
    private final ValidateurReponse validateurReponse;
    private final RegleCollecte regleCollecte;
    private final FormatDate formatDate;

    public CollecteServiceImpl(SondageRepository sondageRepository, ReponseRepository reponseRepository,
                               SondageMapper sondageMapper, ValidateurReponse validateurReponse,
                               RegleCollecte regleCollecte, FormatDate formatDate) {
        this.sondageRepository = sondageRepository;
        this.reponseRepository = reponseRepository;
        this.sondageMapper = sondageMapper;
        this.validateurReponse = validateurReponse;
        this.regleCollecte = regleCollecte;
        this.formatDate = formatDate;
    }

    @Override
    public FormulairePublicModel formulaire(String jetonPublic) {
        return sondageMapper.toFormulairePublicModel(versionOuverte(jetonPublic, Instant.now()));
    }

    @Override
    @Transactional
    public Instant soumettre(String jetonPublic, SoumissionModel soumission) {
        Sondage version = versionOuverte(jetonPublic, Instant.now());
        Sondage racine = version.getRacine();

        String jetonRepondantHash = null;
        if (version.isReponseUnique()) {
            String jetonRepondant = uuid(soumission.jetonRepondant());
            jetonRepondantHash = HachageJeton.hacher(jetonRepondant);
            if (reponseRepository.existsByLigneeIdAndJetonRepondantHash(racine.getId(), jetonRepondantHash)) {
                throw dejaRepondu();
            }
        }

        List<Question> questions = version.getSections().stream()
                .flatMap(section -> section.getQuestions().stream())
                .toList();
        List<ReponseQuestion> lignes = validateurReponse.valider(questions, soumission.reponses());

        Reponse reponse = new Reponse();
        reponse.setSondage(version);
        reponse.setLignee(racine);
        reponse.setJetonRepondantHash(jetonRepondantHash);
        for (ReponseQuestion ligne : lignes) {
            ligne.setReponse(reponse);
            reponse.getValeurs().add(ligne);
        }
        // Flush ici : un envoi simultané du même appareil heurte la contrainte unique (409 DEJA_REPONDU).
        reponse = reponseRepository.saveAndFlush(reponse);
        log.info("Réponse {} enregistrée pour le sondage {} (version {})",
                reponse.getId(), version.getId(), version.getVersion());
        return reponse.getDateSoumission();
    }

    /** Version EN_COURS de la lignée du lien, si l'entreprise peut collecter. */
    private Sondage versionOuverte(String jetonPublic, Instant maintenant) {
        Sondage racine = sondageRepository.findByJetonPublic(jetonPublic)
                .orElseThrow(() -> new ResourceNotFoundException("SONDAGE_INTROUVABLE", "Ce lien de sondage n'existe pas."));
        if (!regleCollecte.entrepriseAutorisee(racine.getEntreprise(), maintenant)) {
            throw new RessourceFermeeException("SONDAGE_FERME", MESSAGE_FERME);
        }
        List<Sondage> lignee = sondageRepository.findLignee(racine.getId());
        Optional<Sondage> ouverte = lignee.stream()
                .filter(v -> RegleSondage.statut(v, maintenant) == StatutSondage.EN_COURS)
                .max(Comparator.comparingInt(Sondage::getVersion));
        if (ouverte.isPresent()) {
            return ouverte.get();
        }
        Optional<Sondage> planifiee = lignee.stream()
                .filter(v -> v.isPublie() && RegleSondage.statut(v, maintenant) == StatutSondage.NON_COMMENCE)
                .min(Comparator.comparing(Sondage::getDatePublication));
        if (planifiee.isPresent()) {
            throw new ActionInterditeException("SONDAGE_NON_OUVERT",
                    "Ce sondage ouvrira le " + formatDate.jour(planifiee.get().getDatePublication()) + ".");
        }
        throw new RessourceFermeeException("SONDAGE_FERME", MESSAGE_FERME);
    }

    /** Jeton de l'appareil : un UUID, obligatoire pour un sondage à réponse unique. */
    private static String uuid(String jetonRepondant) {
        try {
            return UUID.fromString(jetonRepondant == null ? "" : jetonRepondant.trim()).toString().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw new RegleGestionException("JETON_REPONDANT_INVALIDE",
                    "Ce sondage n'accepte qu'une réponse par appareil : jetonRepondant (UUID) est obligatoire.");
        }
    }

    private static ConflitException dejaRepondu() {
        return new ConflitException("DEJA_REPONDU", "Vous avez déjà répondu à ce sondage depuis cet appareil.");
    }
}
