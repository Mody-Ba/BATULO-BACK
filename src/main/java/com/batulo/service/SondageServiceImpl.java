package com.batulo.service;

import com.batulo.entity.OptionQuestion;
import com.batulo.entity.Question;
import com.batulo.entity.Section;
import com.batulo.entity.Sondage;
import com.batulo.entity.Utilisateur;
import com.batulo.enums.Role;
import com.batulo.enums.StatutSondage;
import com.batulo.exception.ActionInterditeException;
import com.batulo.exception.ConflitException;
import com.batulo.exception.RegleGestionException;
import com.batulo.exception.ResourceNotFoundException;
import com.batulo.repository.AssignationRepository;
import com.batulo.repository.SondageRepository;
import com.batulo.repository.SondageSpecifications;
import com.batulo.repository.UtilisateurRepository;
import com.batulo.service.mappers.SondageMapper;
import com.batulo.service.model.ApercuModel;
import com.batulo.service.model.CreationSondageModel;
import com.batulo.service.model.FormulaireModel;
import com.batulo.service.model.DatesSondageModel;
import com.batulo.service.model.OptionInfoModel;
import com.batulo.service.model.QuestionInfoModel;
import com.batulo.service.model.SectionInfoModel;
import com.batulo.service.model.SondageInfoModel;
import com.batulo.service.model.SondageModel;
import com.batulo.service.model.SondageResumeModel;
import com.batulo.service.model.VersionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class SondageServiceImpl implements SondageService {

    private static final Logger log = LoggerFactory.getLogger(SondageServiceImpl.class);

    static final int SECTIONS_MAX = 50;
    static final int QUESTIONS_MAX_PAR_SECTION = 100;
    private static final int VALEUR_MAX = 100;
    private static final int TITRE_MAX = 200;

    private final SondageRepository sondageRepository;
    private final AssignationRepository assignationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final SondageMapper sondageMapper;
    private final ValidateurQuestion validateurQuestion;

    public SondageServiceImpl(SondageRepository sondageRepository,
                              AssignationRepository assignationRepository,
                              UtilisateurRepository utilisateurRepository,
                              SondageMapper sondageMapper,
                              ValidateurQuestion validateurQuestion) {
        this.sondageRepository = sondageRepository;
        this.assignationRepository = assignationRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.sondageMapper = sondageMapper;
        this.validateurQuestion = validateurQuestion;
    }

    // ---------------------------------------------------------------- Sondage

    @Override
    @Transactional
    public SondageModel creer(Long utilisateurId, CreationSondageModel creation) {
        Utilisateur acteur = acteur(utilisateurId);
        verifierDroitDeModifier(acteur);
        Sondage sondage;
        if (creation.sondageSourceId() == null) {
            sondage = new Sondage();
            sondage.setEntreprise(acteur.getEntreprise());
            sondage.setCreePar(acteur);
            sondage.setDescription(texteFacultatif(creation.description()));
        } else {
            // Réutilisation : n'importe quel formulaire visible de l'entreprise, même TERMINE. Seul le contenu est
            // copié (mêmes valeurs d'options) : ni dates, ni publication, ni assignations, ni réponses.
            Sondage source = sondageLisible(acteur, creation.sondageSourceId());
            sondage = nouveauBrouillon(source, acteur);
            String description = texteFacultatif(creation.description());
            sondage.setDescription(description != null ? description : source.getDescription());
        }
        sondage.setTitre(creation.titre().trim());
        sondage = sondageRepository.save(sondage);
        log.info("Sondage {} créé par le compte {} (entreprise {}){}", sondage.getId(), acteur.getId(),
                acteur.getEntreprise().getId(),
                creation.sondageSourceId() == null ? "" : " à partir du sondage " + creation.sondageSourceId());
        return enregistrer(sondage);
    }

    @Override
    public Page<FormulaireModel> formulaires(Long utilisateurId, boolean modelesSeulement, int page, int taille) {
        Utilisateur acteur = acteur(utilisateurId);
        // Réutiliser un formulaire, c'est créer un sondage : même permission.
        verifierDroitDeModifier(acteur);
        Specification<Sondage> filtre = SondageSpecifications.deLEntreprise(acteur.getEntreprise().getId());
        if (acteur.getRole() != Role.ADMIN) {
            filtre = filtre.and(SondageSpecifications.visiblesParGestionnaire(acteur.getId()));
        }
        if (modelesSeulement) {
            filtre = filtre.and(SondageSpecifications.modelesSeulement());
        }
        Page<Sondage> sondages = sondageRepository.findAll(filtre, PageRequest.of(page, taille,
                Sort.by(Sort.Order.desc("estModele"), Sort.Order.desc("dateModification"), Sort.Order.desc("id"))));
        Map<Long, long[]> contenu = compterContenu(sondages);
        Instant maintenant = Instant.now();
        return sondages.map(s -> sondageMapper.toFormulaireModel(s, maintenant,
                contenu.getOrDefault(s.getId(), new long[]{0, 0})[1]));
    }

    @Override
    @Transactional
    public SondageModel marquerModele(Long utilisateurId, Long sondageId, boolean estModele) {
        // Métadonnée, pas du contenu : permis quel que soit le statut.
        Sondage sondage = sondageGere(utilisateurId, sondageId);
        sondage.setEstModele(estModele);
        log.info("Sondage {} {} comme modèle par le compte {}", sondageId, estModele ? "marqué" : "démarqué",
                utilisateurId);
        return enregistrer(sondage);
    }

    @Override
    public Page<SondageResumeModel> lister(Long utilisateurId, StatutSondage statut, int page, int taille) {
        Utilisateur acteur = acteur(utilisateurId);
        Instant maintenant = Instant.now();
        PageRequest pageRequest = PageRequest.of(page, taille, Sort.by(Sort.Direction.DESC, "dateModification", "id"));
        // L'admin voit tous les sondages de l'entreprise ; le gestionnaire, les lignées qu'il a créées ou qui lui sont assignées.
        Specification<Sondage> filtre = SondageSpecifications.deLEntreprise(acteur.getEntreprise().getId())
                .and(SondageSpecifications.parStatut(statut, maintenant));
        if (acteur.getRole() != Role.ADMIN) {
            filtre = filtre.and(SondageSpecifications.visiblesParGestionnaire(acteur.getId()));
        }
        Page<Sondage> sondages = sondageRepository.findAll(filtre, pageRequest);

        Map<Long, long[]> contenu = compterContenu(sondages);
        return sondages.map(s -> {
            long[] nombres = contenu.getOrDefault(s.getId(), new long[]{0, 0});
            return sondageMapper.toResumeModel(s, maintenant, nombres[0], nombres[1]);
        });
    }

    @Override
    public SondageModel detail(Long utilisateurId, Long sondageId) {
        return sondageMapper.toModel(sondageLisible(acteur(utilisateurId), sondageId), Instant.now());
    }

    @Override
    @Transactional
    public SondageModel modifier(Long utilisateurId, Long sondageId, SondageInfoModel info) {
        Sondage sondage = sondageModifiable(utilisateurId, sondageId);
        sondage.setTitre(info.titre().trim());
        sondage.setDescription(texteFacultatif(info.description()));
        sondage.marquerModifie();
        return enregistrer(sondage);
    }

    @Override
    @Transactional
    public void supprimer(Long utilisateurId, Long sondageId) {
        Sondage sondage = sondageGere(utilisateurId, sondageId);
        if (sondage.isPublie()) {
            throw new ConflitException("SONDAGE_PUBLIE",
                    "Un sondage publié ne se supprime pas : fermez-le (ses réponses sont conservées).");
        }
        sondageRepository.delete(sondage);
        log.info("Sondage {} supprimé par le compte {}", sondageId, utilisateurId);
    }

    // ---------------------------------------------------------------- Sections

    @Override
    @Transactional
    public SondageModel ajouterSection(Long utilisateurId, Long sondageId, SectionInfoModel info) {
        Sondage sondage = sondageModifiable(utilisateurId, sondageId);
        if (sondage.getSections().size() >= SECTIONS_MAX) {
            throw new RegleGestionException("LIMITE_ATTEINTE",
                    "Un sondage ne peut pas avoir plus de " + SECTIONS_MAX + " sections.");
        }
        Section section = new Section();
        section.setSondage(sondage);
        section.setTitre(info.titre().trim());
        section.setDescription(texteFacultatif(info.description()));
        section.setOrdre(sondage.getSections().size() + 1);
        sondage.getSections().add(section);
        sondage.marquerModifie();
        return enregistrer(sondage);
    }

    @Override
    @Transactional
    public SondageModel modifierSection(Long utilisateurId, Long sondageId, Long sectionId, SectionInfoModel info) {
        Sondage sondage = sondageModifiable(utilisateurId, sondageId);
        Section section = section(sondage, sectionId);
        section.setTitre(info.titre().trim());
        section.setDescription(texteFacultatif(info.description()));
        sondage.marquerModifie();
        return enregistrer(sondage);
    }

    @Override
    @Transactional
    public SondageModel supprimerSection(Long utilisateurId, Long sondageId, Long sectionId) {
        Sondage sondage = sondageModifiable(utilisateurId, sondageId);
        sondage.getSections().remove(section(sondage, sectionId));
        numeroter(sondage.getSections(), Section::setOrdre);
        sondage.marquerModifie();
        return enregistrer(sondage);
    }

    @Override
    @Transactional
    public SondageModel ordonnerSections(Long utilisateurId, Long sondageId, List<Long> ids) {
        Sondage sondage = sondageModifiable(utilisateurId, sondageId);
        List<Section> ordonnees = reordonner(sondage.getSections(), ids, Section::getId, "sections du sondage");
        sondage.getSections().clear();
        sondage.getSections().addAll(ordonnees);
        numeroter(sondage.getSections(), Section::setOrdre);
        sondage.marquerModifie();
        return enregistrer(sondage);
    }

    // ---------------------------------------------------------------- Questions

    @Override
    @Transactional
    public SondageModel ajouterQuestion(Long utilisateurId, Long sondageId, Long sectionId, QuestionInfoModel info) {
        Sondage sondage = sondageModifiable(utilisateurId, sondageId);
        Section section = section(sondage, sectionId);
        if (section.getQuestions().size() >= QUESTIONS_MAX_PAR_SECTION) {
            throw new RegleGestionException("LIMITE_ATTEINTE",
                    "Une section ne peut pas avoir plus de " + QUESTIONS_MAX_PAR_SECTION + " questions.");
        }
        Question question = new Question();
        question.setSection(section);
        question.setOrdre(section.getQuestions().size() + 1);
        appliquer(question, info);
        section.getQuestions().add(question);
        sondage.marquerModifie();
        return enregistrer(sondage);
    }

    @Override
    @Transactional
    public SondageModel modifierQuestion(Long utilisateurId, Long sondageId, Long questionId, QuestionInfoModel info) {
        Sondage sondage = sondageModifiable(utilisateurId, sondageId);
        appliquer(question(sondage, questionId), info);
        sondage.marquerModifie();
        return enregistrer(sondage);
    }

    @Override
    @Transactional
    public SondageModel supprimerQuestion(Long utilisateurId, Long sondageId, Long questionId) {
        Sondage sondage = sondageModifiable(utilisateurId, sondageId);
        Question question = question(sondage, questionId);
        Section section = question.getSection();
        section.getQuestions().remove(question);
        numeroter(section.getQuestions(), Question::setOrdre);
        sondage.marquerModifie();
        return enregistrer(sondage);
    }

    @Override
    @Transactional
    public SondageModel ordonnerQuestions(Long utilisateurId, Long sondageId, Long sectionId, List<Long> ids) {
        Sondage sondage = sondageModifiable(utilisateurId, sondageId);
        Section section = section(sondage, sectionId);
        List<Question> ordonnees = reordonner(section.getQuestions(), ids, Question::getId, "questions de la section");
        section.getQuestions().clear();
        section.getQuestions().addAll(ordonnees);
        numeroter(section.getQuestions(), Question::setOrdre);
        sondage.marquerModifie();
        return enregistrer(sondage);
    }

    /** Valide puis recopie le libellé, le type, la configuration et les options dans la question. */
    private void appliquer(Question question, QuestionInfoModel info) {
        List<OptionInfoModel> options = info.options() == null ? List.of() : info.options();
        question.setConfiguration(validateurQuestion.valider(info.type(), info.configuration(), options.size()));
        question.setLibelle(info.libelle().trim());
        question.setAide(texteFacultatif(info.aide()));
        question.setType(info.type());
        question.setObligatoire(info.obligatoire());
        remplacerOptions(question, options);
    }

    /**
     * Remplace les options de la question par la liste demandée, dans cet ordre.
     * <ul>
     *   <li>Avec « id » : option existante ; sa valeur est conservée si « valeur » est absente, même si le
     *   libellé change (les statistiques s'appuient dessus).</li>
     *   <li>Sans « id » : nouvelle option ; sans « valeur », elle est générée depuis le libellé.</li>
     *   <li>Une valeur donnée deux fois : 400 VALEUR_OPTION_EN_DOUBLE.</li>
     * </ul>
     */
    private void remplacerOptions(Question question, List<OptionInfoModel> demandees) {
        Map<Long, OptionQuestion> existantes = question.getOptions().stream()
                .collect(Collectors.toMap(OptionQuestion::getId, Function.identity()));

        // 1. Valeurs imposées (données ou déjà enregistrées) : uniques dans la question.
        List<OptionQuestion> resultat = new ArrayList<>();
        Map<OptionQuestion, String> valeurs = new HashMap<>();
        Set<String> prises = new HashSet<>();
        Set<Long> idsVus = new HashSet<>();
        for (OptionInfoModel demande : demandees) {
            OptionQuestion option;
            if (demande.id() != null) {
                option = existantes.get(demande.id());
                if (option == null || !idsVus.add(demande.id())) {
                    throw new RegleGestionException(ValidateurQuestion.CODE,
                            "L'option " + demande.id() + " n'appartient pas à cette question ou est en double.");
                }
            } else {
                option = new OptionQuestion();
                option.setQuestion(question);
            }
            option.setLibelle(demande.libelle().trim());
            String valeur = demande.valeur() != null ? demande.valeur().trim()
                    : demande.id() != null ? option.getValeur() : null;
            if (valeur != null) {
                if (!prises.add(valeur)) {
                    throw new RegleGestionException("VALEUR_OPTION_EN_DOUBLE",
                            "La valeur « " + valeur + " » est utilisée par deux options de la même question.");
                }
                valeurs.put(option, valeur);
            }
            resultat.add(option);
        }
        // 2. Valeurs générées depuis le libellé pour les nouvelles options qui n'en ont pas.
        for (OptionQuestion option : resultat) {
            if (!valeurs.containsKey(option)) {
                String base = valeurDepuisLibelle(option.getLibelle());
                String valeur = base;
                for (int suffixe = 2; prises.contains(valeur); suffixe++) {
                    valeur = base + "_" + suffixe;
                }
                prises.add(valeur);
                valeurs.put(option, valeur);
            }
        }

        // 3. Application en deux temps : la contrainte unique (question_id, valeur) ne doit jamais voir
        //    deux lignes avec la même valeur au moment d'un flush (échange de valeurs, option remplacée).
        question.getOptions().retainAll(resultat);
        boolean echange = false;
        for (OptionQuestion option : resultat) {
            if (option.getId() != null && !valeurs.get(option).equals(option.getValeur())) {
                option.setValeur("~" + option.getId());
                echange = true;
            }
        }
        sondageRepository.flush();
        for (int i = 0; i < resultat.size(); i++) {
            OptionQuestion option = resultat.get(i);
            option.setValeur(valeurs.get(option));
            option.setOrdre(i + 1);
        }
        if (echange) {
            sondageRepository.flush();
        }
        question.getOptions().clear();
        question.getOptions().addAll(resultat);
    }

    /** « Maïs irrigué » donne « mais_irrigue » ; « option » si le libellé ne contient ni lettre ni chiffre. */
    static String valeurDepuisLibelle(String libelle) {
        String valeur = Normalizer.normalize(libelle, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (valeur.isEmpty()) {
            valeur = "option";
        }
        // Place laissée pour un suffixe « _NN ».
        return valeur.length() > VALEUR_MAX - 4 ? valeur.substring(0, VALEUR_MAX - 4) : valeur;
    }

    // ---------------------------------------------------------------- Publication, versions, copie

    @Override
    @Transactional
    public SondageModel publier(Long utilisateurId, Long sondageId, DatesSondageModel dates) {
        Sondage sondage = sondageGere(utilisateurId, sondageId);
        if (sondage.isPublie()) {
            throw new ConflitException("SONDAGE_PUBLIE", "Ce sondage est déjà publié.");
        }
        verifierComplet(sondage);
        Instant maintenant = Instant.now();
        Instant ouverture = dates.datePublication() == null || dates.datePublication().isBefore(maintenant)
                ? maintenant : dates.datePublication();
        verifierFermeture(dates.dateFermeture(), ouverture, maintenant);
        sondage.setDatePublication(ouverture);
        sondage.setDateFermeture(dates.dateFermeture());
        fermerVersionsPrecedentes(sondage, maintenant);
        log.info("Sondage {} (version {}) publié par le compte {}, ouverture {}",
                sondageId, sondage.getVersion(), utilisateurId, ouverture);
        return enregistrer(sondage);
    }

    @Override
    @Transactional
    public SondageModel modifierDates(Long utilisateurId, Long sondageId, DatesSondageModel dates) {
        Sondage sondage = sondageGere(utilisateurId, sondageId);
        Instant maintenant = Instant.now();
        StatutSondage statut = RegleSondage.statut(sondage, maintenant);
        if (!sondage.isPublie()) {
            throw new ConflitException("SONDAGE_BROUILLON",
                    "Ce sondage n'est pas publié : les dates se fixent à la publication.");
        }
        if (statut == StatutSondage.TERMINE) {
            throw new ConflitException("SONDAGE_TERMINE", "Ce sondage est terminé : ses dates ne changent plus.");
        }
        Instant ouverture = sondage.getDatePublication();
        if (dates.datePublication() != null && !dates.datePublication().equals(ouverture)) {
            if (statut == StatutSondage.EN_COURS) {
                throw new RegleGestionException("DATES_INVALIDES",
                        "Le sondage est déjà ouvert : sa date d'ouverture ne peut plus changer.");
            }
            ouverture = dates.datePublication().isBefore(maintenant) ? maintenant : dates.datePublication();
        }
        verifierFermeture(dates.dateFermeture(), ouverture, maintenant);
        sondage.setDatePublication(ouverture);
        sondage.setDateFermeture(dates.dateFermeture());
        log.info("Sondage {} : dates modifiées par le compte {}", sondageId, utilisateurId);
        return enregistrer(sondage);
    }

    @Override
    @Transactional
    public SondageModel fermer(Long utilisateurId, Long sondageId) {
        Sondage sondage = sondageGere(utilisateurId, sondageId);
        if (!sondage.isPublie()) {
            throw new ConflitException("SONDAGE_BROUILLON",
                    "Un brouillon ne se ferme pas : il n'a jamais été publié (vous pouvez le supprimer).");
        }
        Instant maintenant = Instant.now();
        if (RegleSondage.statut(sondage, maintenant) == StatutSondage.TERMINE) {
            throw new ConflitException("SONDAGE_TERMINE", "Ce sondage est déjà terminé.");
        }
        sondage.setFermeLe(maintenant);
        log.info("Sondage {} fermé manuellement par le compte {}", sondageId, utilisateurId);
        return enregistrer(sondage);
    }

    @Override
    public ApercuModel apercu(Long utilisateurId, Long sondageId) {
        return sondageMapper.toApercuModel(sondageLisible(acteur(utilisateurId), sondageId), Instant.now());
    }

    @Override
    @Transactional
    public SondageModel dupliquer(Long utilisateurId, Long sondageId) {
        Utilisateur acteur = acteur(utilisateurId);
        verifierDroitDeModifier(acteur);
        Sondage source = sondageLisible(acteur, sondageId);

        Sondage copie = nouveauBrouillon(source, acteur);
        String titre = "Copie de " + source.getTitre();
        copie.setTitre(titre.length() > TITRE_MAX ? titre.substring(0, TITRE_MAX) : titre);
        copie = sondageRepository.save(copie);
        log.info("Sondage {} dupliqué en {} par le compte {}", sondageId, copie.getId(), utilisateurId);
        return enregistrer(copie);
    }

    @Override
    @Transactional
    public SondageModel nouvelleVersion(Long utilisateurId, Long sondageId) {
        Utilisateur acteur = acteur(utilisateurId);
        Sondage source = sondageGere(utilisateurId, sondageId);
        if (!source.isPublie()) {
            throw new ConflitException("SONDAGE_BROUILLON",
                    "Ce sondage est un brouillon : modifiez-le directement, sans nouvelle version.");
        }
        List<Sondage> lignee = sondageRepository.findLignee(source.getIdLignee());
        Sondage derniere = lignee.get(lignee.size() - 1);
        if (!derniere.isPublie()) {
            throw new ConflitException("BROUILLON_EXISTANT", "Une nouvelle version est déjà en préparation : "
                    + "modifiez le brouillon " + derniere.getId() + " (version " + derniere.getVersion() + ").");
        }
        if (!derniere.getId().equals(source.getId())) {
            throw new ConflitException("VERSION_OBSOLETE", "Ce n'est pas la dernière version : partez de la version "
                    + derniere.getVersion() + " (sondage " + derniere.getId() + ").");
        }

        Sondage version = nouveauBrouillon(source, acteur);
        version.setTitre(source.getTitre());
        version.setVersion(source.getVersion() + 1);
        version.setLignee(source.getLignee() == null ? source : source.getLignee());
        version = sondageRepository.save(version);
        log.info("Sondage {} : version {} créée ({}) par le compte {}",
                source.getIdLignee(), version.getVersion(), version.getId(), utilisateurId);
        return enregistrer(version);
    }

    @Override
    public List<VersionModel> versions(Long utilisateurId, Long sondageId) {
        Utilisateur acteur = acteur(utilisateurId);
        Sondage sondage = sondageLisible(acteur, sondageId);
        Instant maintenant = Instant.now();
        // Visibilité par lignée : si l'une des versions est visible, toutes le sont.
        return sondageRepository.findLignee(sondage.getIdLignee()).stream()
                .map(version -> sondageMapper.toVersionModel(version, maintenant))
                .toList();
    }

    /**
     * Brouillon avec une copie profonde du contenu de la source, créé par l'acteur et issu de la source
     * (sondageOrigine). La source n'est jamais modifiée ; dates, publication et statut de modèle ne sont pas copiés.
     */
    private Sondage nouveauBrouillon(Sondage source, Utilisateur acteur) {
        Sondage brouillon = new Sondage();
        brouillon.setEntreprise(source.getEntreprise());
        brouillon.setCreePar(acteur);
        brouillon.setSondageOrigine(source);
        brouillon.setDescription(source.getDescription());
        CopieSondage.copierContenu(source, brouillon);
        return brouillon;
    }

    /**
     * Une seule version ouverte à la fois : publier la version N ferme les précédentes au moment où elle s'ouvre
     * (immédiatement, ou à sa date d'ouverture si elle est planifiée). Une précédente pas encore ouverte ne
     * s'ouvrira jamais. Les réponses restent liées à leur version.
     */
    private void fermerVersionsPrecedentes(Sondage publiee, Instant maintenant) {
        Instant ouverture = publiee.getDatePublication();
        for (Sondage precedente : sondageRepository.findLignee(publiee.getIdLignee())) {
            if (precedente.getId().equals(publiee.getId()) || !precedente.isPublie()
                    || RegleSondage.statut(precedente, maintenant) == StatutSondage.TERMINE) {
                continue;
            }
            if (!ouverture.isAfter(maintenant) || ouverture.isBefore(precedente.getDatePublication())) {
                precedente.setFermeLe(maintenant);
            } else if (precedente.getDateFermeture() == null || precedente.getDateFermeture().isAfter(ouverture)) {
                precedente.setDateFermeture(ouverture);
            }
            log.info("Sondage {} (version {}) fermé par la publication de la version {}",
                    precedente.getId(), precedente.getVersion(), publiee.getVersion());
        }
    }

    /** Au moins une question, et aucune section vide. */
    private static void verifierComplet(Sondage sondage) {
        if (sondage.getSections().isEmpty()) {
            throw new RegleGestionException("SONDAGE_INCOMPLET", "Le sondage n'a aucune section : rien à publier.");
        }
        for (Section section : sondage.getSections()) {
            if (section.getQuestions().isEmpty()) {
                throw new RegleGestionException("SONDAGE_INCOMPLET",
                        "La section « " + section.getTitre() + " » n'a aucune question.");
            }
        }
    }

    private static void verifierFermeture(Instant fermeture, Instant ouverture, Instant maintenant) {
        if (fermeture != null && (!fermeture.isAfter(ouverture) || !fermeture.isAfter(maintenant))) {
            throw new RegleGestionException("DATES_INVALIDES",
                    "La date de fermeture doit être postérieure à l'ouverture et à maintenant.");
        }
    }

    // ---------------------------------------------------------------- Droits et recherche

    private Utilisateur acteur(Long utilisateurId) {
        return utilisateurRepository.findWithEntrepriseById(utilisateurId)
                .filter(u -> u.getEntreprise() != null)
                .orElseThrow(() -> new ResourceNotFoundException("UTILISATEUR_INTROUVABLE", "Compte introuvable."));
    }

    /** Admin : tout sondage de l'entreprise ; gestionnaire : ceux qu'il a créés. Sinon 404. */
    private Sondage sondageLisible(Utilisateur acteur, Long sondageId) {
        return sondageRepository.findByIdAndEntrepriseId(sondageId, acteur.getEntreprise().getId())
                .filter(s -> peutLire(acteur, s))
                .orElseThrow(() -> new ResourceNotFoundException("SONDAGE_INTROUVABLE",
                        "Aucun sondage " + sondageId + " accessible."));
    }

    /** Droit de gérer le sondage (publier, fermer, versionner...), quel que soit son statut. */
    /**
     * Admin : tout sondage de l'entreprise. Gestionnaire : la lignée est visible s'il a créé l'une de ses versions
     * ou si elle lui est assignée (GE-04, RG-02).
     */
    private boolean peutLire(Utilisateur acteur, Sondage sondage) {
        if (acteur.getRole() == Role.ADMIN) {
            return true;
        }
        Long lignee = sondage.getIdLignee();
        return sondageRepository.aCreeDansLaLignee(lignee, acteur.getId())
                || assignationRepository.existsBySondageIdAndGestionnaireId(lignee, acteur.getId());
    }

    /**
     * Droit de gérer le sondage (modifier, publier, fermer, versionner, supprimer...), quel que soit son statut :
     * l'admin, ou le gestionnaire qui a créé CETTE version, avec la permission de créer des sondages. Un gestionnaire
     * qui le voit seulement par assignation (ou par sa lignée) ne le gère pas : 403 ACTION_RESERVEE_CREATEUR.
     */
    private Sondage sondageGere(Long utilisateurId, Long sondageId) {
        Utilisateur acteur = acteur(utilisateurId);
        verifierDroitDeModifier(acteur);
        Sondage sondage = sondageLisible(acteur, sondageId);
        if (acteur.getRole() == Role.GESTIONNAIRE
                && (sondage.getCreePar() == null || !sondage.getCreePar().getId().equals(acteur.getId()))) {
            throw new ActionInterditeException("ACTION_RESERVEE_CREATEUR",
                    "Vous pouvez consulter ce sondage, mais seuls son créateur et l'admin peuvent le modifier, "
                            + "le publier ou le fermer.");
        }
        return sondage;
    }

    /** Droit de gérer ET contenu modifiable : seul un brouillon l'est, un sondage publié est gelé. */
    private Sondage sondageModifiable(Long utilisateurId, Long sondageId) {
        Sondage sondage = sondageGere(utilisateurId, sondageId);
        if (sondage.isPublie()) {
            throw new ConflitException("SONDAGE_PUBLIE", "Ce sondage est publié : son contenu est gelé. "
                    + "Créez une nouvelle version (POST /api/sondages/" + sondageId + "/nouvelle-version) pour le modifier.");
        }
        return sondage;
    }

    /** Un gestionnaire sans la permission garde la lecture de ses sondages, mais ne crée ni ne modifie plus. */
    private static void verifierDroitDeModifier(Utilisateur acteur) {
        if (acteur.getRole() == Role.GESTIONNAIRE && !acteur.isPeutCreerSondages()) {
            throw new ActionInterditeException("PERMISSION_SONDAGES_REQUISE",
                    "Votre admin ne vous a pas donné la permission de créer ou modifier des sondages.");
        }
    }

    private static Section section(Sondage sondage, Long sectionId) {
        return sondage.getSections().stream()
                .filter(s -> s.getId().equals(sectionId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("SECTION_INTROUVABLE",
                        "Aucune section " + sectionId + " dans ce sondage."));
    }

    private static Question question(Sondage sondage, Long questionId) {
        return sondage.getSections().stream()
                .flatMap(s -> s.getQuestions().stream())
                .filter(q -> q.getId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("QUESTION_INTROUVABLE",
                        "Aucune question " + questionId + " dans ce sondage."));
    }

    /** Renvoie les éléments dans l'ordre des ids, qui doivent être exactement tous les ids actuels. */
    private static <T> List<T> reordonner(List<T> elements, List<Long> ids, Function<T, Long> id, String quoi) {
        Map<Long, T> parId = elements.stream().collect(Collectors.toMap(id, Function.identity()));
        Set<Long> demandes = new LinkedHashSet<>(ids);
        if (demandes.size() != ids.size() || !demandes.equals(parId.keySet())) {
            throw new RegleGestionException("ORDRE_INVALIDE",
                    "La liste doit contenir exactement une fois chacun des ids des " + quoi + " : " + parId.keySet());
        }
        return ids.stream().map(parId::get).toList();
    }

    private static <T> void numeroter(List<T> elements, ObjIntConsumer<T> setOrdre) {
        for (int i = 0; i < elements.size(); i++) {
            setOrdre.accept(elements.get(i), i + 1);
        }
    }

    /** Nombre de sections et de questions des sondages de la page : sondageId -> [sections, questions]. */
    private Map<Long, long[]> compterContenu(Page<Sondage> sondages) {
        if (sondages.isEmpty()) {
            return Map.of();
        }
        return sondageRepository.compterContenu(sondages.map(Sondage::getId).toList()).stream()
                .collect(Collectors.toMap(ligne -> (Long) ligne[0],
                        ligne -> new long[]{(Long) ligne[1], (Long) ligne[2]}));
    }

    /** Écrit les changements (les nouveaux éléments reçoivent leur id) et renvoie le sondage complet. */
    private SondageModel enregistrer(Sondage sondage) {
        sondageRepository.flush();
        return sondageMapper.toModel(sondage, Instant.now());
    }

    private static String texteFacultatif(String texte) {
        return texte == null || texte.isBlank() ? null : texte.trim();
    }
}
