package com.batulo.service;

import com.batulo.entity.Question;
import com.batulo.entity.Section;
import com.batulo.entity.Sondage;
import com.batulo.enums.GranulariteDate;
import com.batulo.enums.TypeQuestion;
import com.batulo.exception.RegleGestionException;
import com.batulo.exception.ResourceNotFoundException;
import com.batulo.repository.SondageRepository;
import com.batulo.repository.StatistiqueRepository;
import com.batulo.repository.StatistiqueRepository.Filtre;
import com.batulo.service.model.StatistiquesModel;
import com.batulo.service.model.StatistiquesModel.Cellule;
import com.batulo.service.model.StatistiquesModel.Classe;
import com.batulo.service.model.StatistiquesModel.Dates;
import com.batulo.service.model.StatistiquesModel.LigneMatrice;
import com.batulo.service.model.StatistiquesModel.Matrice;
import com.batulo.service.model.StatistiquesModel.Numerique;
import com.batulo.service.model.StatistiquesModel.OptionStat;
import com.batulo.service.model.StatistiquesModel.PageTextes;
import com.batulo.service.model.StatistiquesModel.Periode;
import com.batulo.service.model.StatistiquesModel.QuestionStat;
import com.batulo.service.model.StatistiquesModel.RangStat;
import com.batulo.service.model.StatistiquesModel.Resume;
import com.batulo.service.model.StatistiquesModel.Texte;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class StatistiqueServiceImpl implements StatistiqueService {

    /** Textes renvoyés avec les statistiques ; la suite se lit par la route des textes. */
    static final int TEXTES_PAR_PAGE = 20;
    /** Au-delà, une question NUMERIQUE est présentée en tranches. */
    static final int VALEURS_DISTINCTES_MAX = 20;
    static final int NOMBRE_TRANCHES = 10;
    /** Écart (en jours) jusqu'auquel une question DATE est regroupée par jour. */
    static final long JOURS_MAX_PAR_JOUR = 62;

    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    private final AccesSondage accesSondage;
    private final SondageRepository sondageRepository;
    private final StatistiqueRepository statistiqueRepository;
    private final ZoneId fuseau;

    public StatistiqueServiceImpl(AccesSondage accesSondage, SondageRepository sondageRepository,
                                  StatistiqueRepository statistiqueRepository,
                                  @Value("${batulo.fuseau-horaire}") String fuseauHoraire) {
        this.accesSondage = accesSondage;
        this.sondageRepository = sondageRepository;
        this.statistiqueRepository = statistiqueRepository;
        this.fuseau = ZoneId.of(fuseauHoraire);
    }

    /** Définition de référence d'une question (dernière version qui la contient) et ses occurrences retenues. */
    private static final class Reference {
        private final Question question;
        private final String section;
        /** Ids des questions de même clé et de même type, dans les versions retenues. */
        private final Set<Long> ids = new LinkedHashSet<>();
        private final Set<Long> versions = new HashSet<>();
        /** Libellé de chaque valeur d'option (celui de la version la plus récente qui la contient). */
        private final Map<String, String> libellesOptions = new LinkedHashMap<>();

        private Reference(Question question, String section) {
            this.question = question;
            this.section = section;
        }
    }

    @Override
    public StatistiquesModel statistiques(Long utilisateurId, Long sondageId, boolean lignee, LocalDate du,
                                          LocalDate au, GranulariteDate granulariteDate) {
        Sondage sondage = accesSondage.sondageLisible(accesSondage.acteur(utilisateurId), sondageId);
        List<Sondage> versions = versions(sondage, lignee);
        Map<String, Reference> references = references(versions);
        Filtre filtre = filtre(versions, du, au);

        StatistiqueRepository.Resume resume = statistiqueRepository.resume(filtre);
        Map<Long, Long> reponsesParVersion = statistiqueRepository.reponsesParVersion(filtre);
        Map<TypeQuestion, Set<Long>> idsParType = new HashMap<>();
        references.values().forEach(r ->
                idsParType.computeIfAbsent(r.question.getType(), t -> new LinkedHashSet<>()).addAll(r.ids));

        Calculs calculs = new Calculs(filtre, idsParType, references, granulariteDate);
        List<QuestionStat> questions = references.entrySet().stream()
                .map(e -> calculs.question(e.getKey(), e.getValue(), sansReponseBase(e.getValue(), reponsesParVersion)))
                .toList();

        return new StatistiquesModel(sondage.getId(), sondage.getTitre(), lignee,
                versions.stream().map(Sondage::getVersion).toList(), du, au,
                new Resume(resume.nombre(), resume.premiere(), resume.derniere()), questions);
    }

    @Override
    public PageTextes textes(Long utilisateurId, Long sondageId, Long questionId, boolean lignee, LocalDate du,
                             LocalDate au, int page, int taille) {
        Sondage sondage = accesSondage.sondageLisible(accesSondage.acteur(utilisateurId), sondageId);
        List<Sondage> versions = versions(sondage, lignee);
        Map<String, Reference> references = references(versions);
        Reference reference = references.values().stream()
                .filter(r -> r.ids.contains(questionId) || r.question.getId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("QUESTION_INTROUVABLE",
                        "Aucune question " + questionId + " dans ce sondage."));
        TypeQuestion type = reference.question.getType();
        if (type != TypeQuestion.TEXTE_COURT && type != TypeQuestion.TEXTE_LONG) {
            throw new RegleGestionException("QUESTION_NON_TEXTE", "Cette question n'est pas une question à texte libre.");
        }
        Filtre filtre = filtre(versions, du, au);
        long total = statistiqueRepository.nombreTextes(filtre, reference.ids);
        List<Texte> contenu = statistiqueRepository.pageTextes(filtre, reference.ids, page, taille).stream()
                .map(t -> new Texte(t.texte(), t.date()))
                .toList();
        return new PageTextes(contenu, page, taille, total, (int) ((total + taille - 1) / taille));
    }

    // ---------------------------------------------------------------- Périmètre

    /** Cette version, ou toutes les versions publiées de la lignée (plus celle demandée), de la plus ancienne. */
    private List<Sondage> versions(Sondage sondage, boolean lignee) {
        if (!lignee) {
            return List.of(sondage);
        }
        return sondageRepository.findLignee(sondage.getIdLignee()).stream()
                .filter(v -> v.isPublie() || v.getId().equals(sondage.getId()))
                .sorted(Comparator.comparingInt(Sondage::getVersion))
                .toList();
    }

    /**
     * Une référence par clé de question : la définition de la version la plus récente qui la contient, dans
     * l'ordre de cette version ; les questions retirées depuis viennent ensuite. Une occurrence d'un autre type
     * (type changé entre deux versions) n'est pas retenue.
     */
    private static Map<String, Reference> references(List<Sondage> versions) {
        Map<String, Reference> references = new LinkedHashMap<>();
        for (int i = versions.size() - 1; i >= 0; i--) {
            for (Section section : versions.get(i).getSections()) {
                for (Question question : section.getQuestions()) {
                    references.putIfAbsent(question.getCle(), new Reference(question, section.getTitre()));
                }
            }
        }
        for (Sondage version : versions) {
            for (Section section : version.getSections()) {
                for (Question question : section.getQuestions()) {
                    Reference reference = references.get(question.getCle());
                    if (reference.question.getType() != question.getType()) {
                        continue;
                    }
                    reference.ids.add(question.getId());
                    reference.versions.add(version.getId());
                    question.getOptions().forEach(o -> reference.libellesOptions.put(o.getValeur(), o.getLibelle()));
                }
            }
        }
        // Options de la référence d'abord, dans son ordre ; puis les options retirées depuis.
        for (Reference reference : references.values()) {
            Map<String, String> ordonnees = new LinkedHashMap<>();
            reference.question.getOptions().forEach(o -> ordonnees.put(o.getValeur(), o.getLibelle()));
            reference.libellesOptions.forEach(ordonnees::putIfAbsent);
            reference.libellesOptions.clear();
            reference.libellesOptions.putAll(ordonnees);
        }
        return references;
    }

    private Filtre filtre(List<Sondage> versions, LocalDate du, LocalDate au) {
        if (du != null && au != null && du.isAfter(au)) {
            throw new RegleGestionException("PERIODE_INVALIDE", "La date « du » doit être antérieure ou égale à « au ».");
        }
        return new Filtre(
                versions.stream().map(Sondage::getId).toList(),
                du == null ? null : du.atStartOfDay(fuseau).toInstant(),
                au == null ? null : au.plusDays(1).atStartOfDay(fuseau).toInstant());
    }

    /** Réponses aux versions qui contiennent la question : base de « sansReponse ». */
    private static long sansReponseBase(Reference reference, Map<Long, Long> reponsesParVersion) {
        return reference.versions.stream().mapToLong(v -> reponsesParVersion.getOrDefault(v, 0L)).sum();
    }

    // ---------------------------------------------------------------- Calculs par type

    /** Lance chaque agrégation une seule fois pour toutes les questions concernées, puis met en forme. */
    private final class Calculs {

        private final Map<String, Long> repondants;
        private final Map<String, List<StatistiqueRepository.Option>> options;
        private final Map<String, List<StatistiqueRepository.Booleen>> booleens;
        private final Map<String, StatistiqueRepository.Numerique> numeriques;
        private final Map<String, List<StatistiqueRepository.Valeur>> valeurs;
        private final Map<String, List<StatistiqueRepository.Tranche>> tranches;
        private final Map<String, List<StatistiqueRepository.Jour>> jours;
        private final Map<String, List<StatistiqueRepository.Rang>> rangs;
        private final Map<String, List<StatistiqueRepository.Cellule>> cellules;
        private final Map<String, List<StatistiqueRepository.Texte>> textes;
        private final GranulariteDate granulariteDate;

        Calculs(Filtre filtre, Map<TypeQuestion, Set<Long>> ids, Map<String, Reference> references,
                GranulariteDate granulariteDate) {
            this.granulariteDate = granulariteDate;
            Set<Long> toutes = ids.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
            repondants = toutes.isEmpty() ? Map.of() : statistiqueRepository.repondants(filtre, toutes);
            options = grouper(lancer(ids, Set.of(TypeQuestion.CHOIX_UNIQUE, TypeQuestion.CHOIX_MULTIPLE),
                    i -> statistiqueRepository.options(filtre, i)), StatistiqueRepository.Option::cle);
            booleens = grouper(lancer(ids, Set.of(TypeQuestion.OUI_NON),
                    i -> statistiqueRepository.booleens(filtre, i)), StatistiqueRepository.Booleen::cle);
            numeriques = lancer(ids, Set.of(TypeQuestion.ECHELLE, TypeQuestion.NUMERIQUE),
                    i -> statistiqueRepository.numeriques(filtre, i)).stream()
                    .collect(Collectors.toMap(StatistiqueRepository.Numerique::cle, Function.identity()));

            // Valeur par valeur : ECHELLE, et NUMERIQUE avec peu de valeurs distinctes ; sinon 10 tranches.
            Set<Long> parValeur = new HashSet<>(ids.getOrDefault(TypeQuestion.ECHELLE, Set.of()));
            Set<Long> enTranches = new HashSet<>();
            references.forEach((cle, r) -> {
                if (r.question.getType() == TypeQuestion.NUMERIQUE) {
                    StatistiqueRepository.Numerique n = numeriques.get(cle);
                    (n != null && n.distinctes() > VALEURS_DISTINCTES_MAX ? enTranches : parValeur).addAll(r.ids);
                }
            });
            valeurs = grouper(parValeur.isEmpty() ? List.of() : statistiqueRepository.valeurs(filtre, parValeur),
                    StatistiqueRepository.Valeur::cle);
            tranches = grouper(enTranches.isEmpty() ? List.of() : statistiqueRepository.tranches(filtre, enTranches),
                    StatistiqueRepository.Tranche::cle);

            jours = grouper(lancer(ids, Set.of(TypeQuestion.DATE), i -> statistiqueRepository.jours(filtre, i)),
                    StatistiqueRepository.Jour::cle);
            rangs = grouper(lancer(ids, Set.of(TypeQuestion.CLASSEMENT), i -> statistiqueRepository.rangs(filtre, i)),
                    StatistiqueRepository.Rang::cle);
            cellules = grouper(lancer(ids, Set.of(TypeQuestion.MATRICE), i -> statistiqueRepository.cellules(filtre, i)),
                    StatistiqueRepository.Cellule::cle);
            textes = grouper(lancer(ids, Set.of(TypeQuestion.TEXTE_COURT, TypeQuestion.TEXTE_LONG),
                    i -> statistiqueRepository.premiersTextes(filtre, i, TEXTES_PAR_PAGE)), StatistiqueRepository.Texte::cle);
        }

        QuestionStat question(String cle, Reference reference, long reponsesAuxVersions) {
            Question question = reference.question;
            long nombre = repondants.getOrDefault(cle, 0L);
            List<OptionStat> optionStats = null;
            Numerique numerique = null;
            Dates dates = null;
            List<RangStat> classement = null;
            Matrice matrice = null;
            PageTextes pageTextes = null;
            switch (question.getType()) {
                case CHOIX_UNIQUE, CHOIX_MULTIPLE -> optionStats = options(reference, cle, nombre);
                case OUI_NON -> optionStats = ouiNon(cle, nombre);
                case ECHELLE, NUMERIQUE -> numerique = numerique(question, cle, nombre);
                case DATE -> dates = dates(cle);
                case CLASSEMENT -> classement = classement(reference, cle);
                case MATRICE -> matrice = matrice(question.getConfiguration(), cle);
                case TEXTE_COURT, TEXTE_LONG -> pageTextes = new PageTextes(
                        textes.getOrDefault(cle, List.of()).stream().map(t -> new Texte(t.texte(), t.date())).toList(),
                        0, TEXTES_PAR_PAGE, nombre, (int) ((nombre + TEXTES_PAR_PAGE - 1) / TEXTES_PAR_PAGE));
            }
            return new QuestionStat(question.getId(), cle, reference.section, question.getLibelle(), question.getType(),
                    nombre, Math.max(0, reponsesAuxVersions - nombre), optionStats, numerique, dates, classement,
                    matrice, pageTextes);
        }

        /** Toutes les options (même à 0), celles de la référence d'abord. */
        private List<OptionStat> options(Reference reference, String cle, long repondantsQuestion) {
            Map<String, Long> parValeur = new HashMap<>();
            options.getOrDefault(cle, List.of()).forEach(o -> parValeur.put(o.valeur(), o.nombre()));
            return reference.libellesOptions.entrySet().stream()
                    .map(e -> {
                        long n = parValeur.getOrDefault(e.getKey(), 0L);
                        return new OptionStat(e.getKey(), e.getValue(), n, pourcentage(n, repondantsQuestion));
                    })
                    .toList();
        }

        private List<OptionStat> ouiNon(String cle, long repondantsQuestion) {
            Map<Boolean, Long> parValeur = new HashMap<>();
            booleens.getOrDefault(cle, List.of()).forEach(b -> parValeur.put(b.valeur(), b.nombre()));
            long oui = parValeur.getOrDefault(true, 0L);
            long non = parValeur.getOrDefault(false, 0L);
            return List.of(new OptionStat("true", "Oui", oui, pourcentage(oui, repondantsQuestion)),
                    new OptionStat("false", "Non", non, pourcentage(non, repondantsQuestion)));
        }

        private Numerique numerique(Question question, String cle, long repondantsQuestion) {
            StatistiqueRepository.Numerique n = numeriques.get(cle);
            if (n == null) {
                return new Numerique(null, null, null, null, List.of());
            }
            List<Classe> distribution = new ArrayList<>();
            if (tranches.containsKey(cle)) {
                Map<Integer, Long> parTranche = new HashMap<>();
                tranches.get(cle).forEach(t -> parTranche.put(t.tranche(), t.nombre()));
                BigDecimal largeur = n.max().subtract(n.min())
                        .divide(BigDecimal.valueOf(NOMBRE_TRANCHES), 4, RoundingMode.HALF_UP);
                for (int i = 1; i <= NOMBRE_TRANCHES; i++) {
                    long nombre = parTranche.getOrDefault(i, 0L);
                    BigDecimal de = n.min().add(largeur.multiply(BigDecimal.valueOf(i - 1)));
                    BigDecimal a = i == NOMBRE_TRANCHES ? n.max() : n.min().add(largeur.multiply(BigDecimal.valueOf(i)));
                    distribution.add(new Classe(null, net(de), net(a), nombre, pourcentage(nombre, repondantsQuestion)));
                }
            } else {
                Map<BigDecimal, Long> parValeur = new LinkedHashMap<>();
                valeurs.getOrDefault(cle, List.of()).stream()
                        .sorted(Comparator.comparing(StatistiqueRepository.Valeur::valeur))
                        .forEach(v -> parValeur.merge(net(v.valeur()), v.nombre(), Long::sum));
                if (question.getType() == TypeQuestion.ECHELLE) {
                    // Toutes les notes de l'échelle, même celles que personne n'a données.
                    JsonNode config = question.getConfiguration();
                    for (int note = config.path("min").asInt(1); note <= config.path("max").asInt(5); note++) {
                        long nombre = parValeur.getOrDefault(net(BigDecimal.valueOf(note)), 0L);
                        distribution.add(new Classe(BigDecimal.valueOf(note), null, null, nombre,
                                pourcentage(nombre, repondantsQuestion)));
                    }
                } else {
                    parValeur.forEach((valeur, nombre) -> distribution.add(
                            new Classe(valeur, null, null, nombre, pourcentage(nombre, repondantsQuestion))));
                }
            }
            return new Numerique(n.moyenne().setScale(2, RoundingMode.HALF_UP), net(n.mediane()), net(n.min()),
                    net(n.max()), distribution);
        }

        private Dates dates(String cle) {
            List<StatistiqueRepository.Jour> parJour = jours.getOrDefault(cle, List.of());
            if (parJour.isEmpty()) {
                return new Dates(null, null, granulariteDate == null ? GranulariteDate.JOUR : granulariteDate, List.of());
            }
            LocalDate min = parJour.get(0).jour();
            LocalDate max = parJour.get(parJour.size() - 1).jour();
            GranulariteDate granularite = granulariteDate != null ? granulariteDate
                    : ChronoUnit.DAYS.between(min, max) <= JOURS_MAX_PAR_JOUR ? GranulariteDate.JOUR : GranulariteDate.MOIS;
            Map<String, Long> repartition = new LinkedHashMap<>();
            parJour.forEach(j -> repartition.merge(granularite == GranulariteDate.JOUR
                    ? j.jour().toString() : j.jour().toString().substring(0, 7), j.nombre(), Long::sum));
            return new Dates(min, max, granularite,
                    repartition.entrySet().stream().map(e -> new Periode(e.getKey(), e.getValue())).toList());
        }

        /** Du mieux classé (rang moyen le plus bas) au moins bien classé. */
        private List<RangStat> classement(Reference reference, String cle) {
            return rangs.getOrDefault(cle, List.of()).stream()
                    .map(r -> new RangStat(r.valeur(), reference.libellesOptions.getOrDefault(r.valeur(), r.valeur()),
                            r.rangMoyen().setScale(2, RoundingMode.HALF_UP), r.nombrePremier()))
                    .sorted(Comparator.comparing(RangStat::rangMoyen).thenComparing(RangStat::libelle))
                    .toList();
        }

        /** Lignes et colonnes de la référence ; pourcentages sur les réponses de chaque ligne. */
        private Matrice matrice(JsonNode configuration, String cle) {
            List<String> colonnes = new ArrayList<>();
            configuration.path("colonnes").forEach(c -> colonnes.add(c.asText()));
            Map<String, Map<String, Long>> parLigne = new LinkedHashMap<>();
            configuration.path("lignes").forEach(l -> parLigne.put(l.asText(), new HashMap<>()));
            for (StatistiqueRepository.Cellule c : cellules.getOrDefault(cle, List.of())) {
                parLigne.computeIfAbsent(c.ligne(), l -> new HashMap<>()).merge(c.colonne(), c.nombre(), Long::sum);
                if (!colonnes.contains(c.colonne())) {
                    colonnes.add(c.colonne());
                }
            }
            List<LigneMatrice> lignes = parLigne.entrySet().stream()
                    .map(e -> {
                        long totalLigne = e.getValue().values().stream().mapToLong(Long::longValue).sum();
                        return new LigneMatrice(e.getKey(), totalLigne, colonnes.stream()
                                .map(c -> {
                                    long n = e.getValue().getOrDefault(c, 0L);
                                    return new Cellule(c, n, pourcentage(n, totalLigne));
                                })
                                .toList());
                    })
                    .toList();
            return new Matrice(colonnes, lignes);
        }

        private <T> List<T> lancer(Map<TypeQuestion, Set<Long>> ids, Set<TypeQuestion> types,
                                   Function<Collection<Long>, List<T>> requete) {
            Set<Long> concernes = types.stream().flatMap(t -> ids.getOrDefault(t, Set.of()).stream())
                    .collect(Collectors.toSet());
            return concernes.isEmpty() ? List.of() : requete.apply(concernes);
        }

        private <T> Map<String, List<T>> grouper(List<T> lignes, Function<T, String> cle) {
            return lignes.stream().collect(Collectors.groupingBy(cle, LinkedHashMap::new, Collectors.toList()));
        }
    }

    /** Pourcentage arrondi à 0,1 ; 0 si la base est nulle. */
    static BigDecimal pourcentage(long nombre, long base) {
        if (base == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return BigDecimal.valueOf(nombre).multiply(CENT).divide(BigDecimal.valueOf(base), 1, RoundingMode.HALF_UP);
    }

    /** 4.0000 donne 4 et 12.50 donne 12.5, sans notation scientifique. */
    static BigDecimal net(BigDecimal valeur) {
        if (valeur == null) {
            return null;
        }
        BigDecimal reduit = valeur.stripTrailingZeros();
        return reduit.scale() < 0 ? reduit.setScale(0) : reduit;
    }
}
