package com.batulo.service;

import com.batulo.entity.OptionQuestion;
import com.batulo.entity.Question;
import com.batulo.entity.Section;
import com.batulo.entity.Sondage;
import com.batulo.entity.Utilisateur;
import com.batulo.enums.Role;
import com.batulo.exception.ActionInterditeException;
import com.batulo.exception.RegleGestionException;
import com.batulo.exception.ResourceNotFoundException;
import com.batulo.repository.SondageRepository;
import com.batulo.repository.UtilisateurRepository;
import com.batulo.service.mappers.SondageMapper;
import com.batulo.service.model.OptionInfoModel;
import com.batulo.service.model.QuestionInfoModel;
import com.batulo.service.model.SectionInfoModel;
import com.batulo.service.model.SondageInfoModel;
import com.batulo.service.model.SondageModel;
import com.batulo.service.model.SondageResumeModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
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

    private final SondageRepository sondageRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final SondageMapper sondageMapper;
    private final ValidateurQuestion validateurQuestion;

    public SondageServiceImpl(SondageRepository sondageRepository,
                              UtilisateurRepository utilisateurRepository,
                              SondageMapper sondageMapper,
                              ValidateurQuestion validateurQuestion) {
        this.sondageRepository = sondageRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.sondageMapper = sondageMapper;
        this.validateurQuestion = validateurQuestion;
    }

    // ---------------------------------------------------------------- Sondage

    @Override
    @Transactional
    public SondageModel creer(Long utilisateurId, SondageInfoModel info) {
        Utilisateur acteur = acteur(utilisateurId);
        verifierDroitDeModifier(acteur);
        Sondage sondage = new Sondage();
        sondage.setEntreprise(acteur.getEntreprise());
        sondage.setCreePar(acteur);
        sondage.setTitre(info.titre().trim());
        sondage.setDescription(texteFacultatif(info.description()));
        sondage = sondageRepository.save(sondage);
        log.info("Sondage {} créé par le compte {} (entreprise {})",
                sondage.getId(), acteur.getId(), acteur.getEntreprise().getId());
        return sondageMapper.toModel(sondage);
    }

    @Override
    public Page<SondageResumeModel> lister(Long utilisateurId, int page, int taille) {
        Utilisateur acteur = acteur(utilisateurId);
        Long entrepriseId = acteur.getEntreprise().getId();
        PageRequest pageRequest = PageRequest.of(page, taille, Sort.by(Sort.Direction.DESC, "dateModification", "id"));
        // L'admin voit tous les sondages de l'entreprise ; le gestionnaire, ceux qu'il a créés (assignation : 2.3).
        Page<Sondage> sondages = acteur.getRole() == Role.ADMIN
                ? sondageRepository.findByEntrepriseId(entrepriseId, pageRequest)
                : sondageRepository.findByEntrepriseIdAndCreeParId(entrepriseId, acteur.getId(), pageRequest);

        Map<Long, long[]> contenu = sondages.isEmpty() ? Map.of()
                : sondageRepository.compterContenu(sondages.map(Sondage::getId).toList()).stream()
                .collect(Collectors.toMap(ligne -> (Long) ligne[0],
                        ligne -> new long[]{(Long) ligne[1], (Long) ligne[2]}));
        return sondages.map(s -> {
            long[] nombres = contenu.getOrDefault(s.getId(), new long[]{0, 0});
            return sondageMapper.toResumeModel(s, nombres[0], nombres[1]);
        });
    }

    @Override
    public SondageModel detail(Long utilisateurId, Long sondageId) {
        return sondageMapper.toModel(sondageLisible(acteur(utilisateurId), sondageId));
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
        Sondage sondage = sondageModifiable(utilisateurId, sondageId);
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

    // ---------------------------------------------------------------- Droits et recherche

    private Utilisateur acteur(Long utilisateurId) {
        return utilisateurRepository.findWithEntrepriseById(utilisateurId)
                .filter(u -> u.getEntreprise() != null)
                .orElseThrow(() -> new ResourceNotFoundException("UTILISATEUR_INTROUVABLE", "Compte introuvable."));
    }

    /** Admin : tout sondage de l'entreprise ; gestionnaire : ceux qu'il a créés. Sinon 404. */
    private Sondage sondageLisible(Utilisateur acteur, Long sondageId) {
        return sondageRepository.findByIdAndEntrepriseId(sondageId, acteur.getEntreprise().getId())
                .filter(s -> acteur.getRole() == Role.ADMIN
                        || (s.getCreePar() != null && s.getCreePar().getId().equals(acteur.getId())))
                .orElseThrow(() -> new ResourceNotFoundException("SONDAGE_INTROUVABLE",
                        "Aucun sondage " + sondageId + " accessible."));
    }

    private Sondage sondageModifiable(Long utilisateurId, Long sondageId) {
        Utilisateur acteur = acteur(utilisateurId);
        verifierDroitDeModifier(acteur);
        return sondageLisible(acteur, sondageId);
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

    /** Écrit les changements (les nouveaux éléments reçoivent leur id) et renvoie le sondage complet. */
    private SondageModel enregistrer(Sondage sondage) {
        sondageRepository.flush();
        return sondageMapper.toModel(sondage);
    }

    private static String texteFacultatif(String texte) {
        return texte == null || texte.isBlank() ? null : texte.trim();
    }
}
