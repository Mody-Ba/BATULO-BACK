package com.batulo.service;

import com.batulo.entity.Question;
import com.batulo.entity.Section;
import com.batulo.entity.Sondage;
import com.batulo.enums.TypeQuestion;
import com.batulo.exception.RegleGestionException;
import com.batulo.repository.SondageRepository;
import com.batulo.repository.StatistiqueRepository;
import com.batulo.repository.StatistiqueRepository.Filtre;
import com.batulo.service.model.CritereModel;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Périmètre d'une analyse, partagé par les statistiques et les exports : droits (AccesSondage), versions retenues
 * (une version ou la lignée), questions de référence regroupées par clé, filtre SQL (période, critères de
 * segmentation) et découpage des critères. Composant non transactionnel : appelé depuis les transactions des services.
 */
@Component
public class AnalysePerimetre {

    /** Tranches d'un critère NUMERIQUE sans tranches définies (entre son min et son max). */
    static final int TRANCHES_PAR_DEFAUT = 5;

    private final AccesSondage accesSondage;
    private final SondageRepository sondageRepository;
    private final StatistiqueRepository statistiqueRepository;
    private final ZoneId fuseau;

    public AnalysePerimetre(AccesSondage accesSondage, SondageRepository sondageRepository,
                            StatistiqueRepository statistiqueRepository,
                            @Value("${batulo.fuseau-horaire}") String fuseauHoraire) {
        this.accesSondage = accesSondage;
        this.sondageRepository = sondageRepository;
        this.statistiqueRepository = statistiqueRepository;
        this.fuseau = ZoneId.of(fuseauHoraire);
    }

    /** Le périmètre d'un calcul : versions retenues, références des questions, filtre (période, critères). */
    record Perimetre(Sondage sondage, List<Sondage> versions, Map<String, Reference> references,
                             Filtre filtre, List<CritereModel> criteres) {
    }

    /** Définition de référence d'une question (dernière version qui la contient) et ses occurrences retenues. */
    static final class Reference {
        final Question question;
        final String section;
        /** Ids des questions de même clé et de même type, dans les versions retenues. */
        final Set<Long> ids = new LinkedHashSet<>();
        final Set<Long> versions = new HashSet<>();
        /** Libellé de chaque valeur d'option (celui de la version la plus récente qui la contient). */
        final Map<String, String> libellesOptions = new LinkedHashMap<>();

        Reference(Question question, String section) {
            this.question = question;
            this.section = section;
        }
    }

    Perimetre perimetre(Long utilisateurId, Long sondageId, boolean lignee, LocalDate du, LocalDate au,
                                Map<String, List<String>> criteres) {
        Sondage sondage = accesSondage.sondageLisible(accesSondage.acteur(utilisateurId), sondageId);
        List<Sondage> versions = versions(sondage, lignee);
        Map<String, Reference> references = references(versions);
        Filtre base = filtre(versions, du, au);
        List<CritereModel> appliques = new ArrayList<>();
        List<StatistiqueRepository.FiltreCritere> filtres = filtresCriteres(criteres, references, base, appliques);
        return new Perimetre(sondage, versions, references,
                new Filtre(base.versions(), base.debut(), base.fin(), filtres), appliques);
    }

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

    /**
     * Filtres de segmentation « cle -> valeurs » : chaque clé doit être un critère, chaque valeur l'une de ses valeurs
     * (DATE : AAAA-MM ou AAAA selon la granularité).
     */
    private List<StatistiqueRepository.FiltreCritere> filtresCriteres(Map<String, List<String>> demandes,
                                                                     Map<String, Reference> references,
                                                                     Filtre base, List<CritereModel> appliques) {
        List<StatistiqueRepository.FiltreCritere> filtres = new ArrayList<>();
        demandes.forEach((cle, valeurs) -> {
            Reference reference = critere(references, cle);
            StatistiqueRepository.Decoupage decoupage = decoupage(reference);
            Map<String, String> autorisees = new LinkedHashMap<>();
            if (reference.question.getType() == TypeQuestion.DATE) {
                String motif = "ANNEE".equals(decoupage.granularite()) ? "\\d{4}" : "\\d{4}-\\d{2}";
                valeurs.stream().filter(v -> v.matches(motif)).forEach(v -> autorisees.put(v, v));
            } else {
                valeursCritere(reference, base).forEach(v -> autorisees.put(v.valeur(), v.libelle()));
            }
            for (String valeur : valeurs) {
                if (!autorisees.containsKey(valeur)) {
                    throw new RegleGestionException("CRITERE_INVALIDE", "« " + valeur + " » n'est pas une valeur du critère « "
                            + reference.question.getLibelle() + " ».");
                }
            }
            List<String> distinctes = valeurs.stream().distinct().toList();
            filtres.add(new StatistiqueRepository.FiltreCritere(reference.ids, decoupage, distinctes));
            appliques.add(new CritereModel(cle, reference.question.getId(), reference.question.getLibelle(),
                    reference.question.getType(), distinctes.stream()
                    .map(v -> new CritereModel.ValeurCritere(v, autorisees.get(v))).toList()));
        });
        return filtres;
    }

    /** La question de cette clé doit être un critère d'analyse (dans sa version la plus récente). */
    static Reference critere(Map<String, Reference> references, String cle) {
        Reference reference = references.get(cle);
        if (reference == null || !reference.question.isEstCritere()) {
            throw new RegleGestionException("CRITERE_INVALIDE",
                    "« " + cle + " » n'est pas un critère d'analyse de ce sondage.");
        }
        return reference;
    }

    /** Découpage du critère : tranches (explicites, ou 5 tranches égales entre min et max), granularité des dates. */
    static StatistiqueRepository.Decoupage decoupage(Reference reference) {
        Question question = reference.question;
        JsonNode config = question.getCritereConfig();
        List<BigDecimal> bornes = List.of();
        if (question.getType() == TypeQuestion.NUMERIQUE) {
            bornes = new ArrayList<>();
            if (config != null && config.has("tranches")) {
                for (JsonNode borne : config.get("tranches")) {
                    bornes.add(borne.decimalValue());
                }
            } else {
                JsonNode configuration = question.getConfiguration();
                BigDecimal min = configuration.get("min").decimalValue();
                BigDecimal max = configuration.get("max").decimalValue();
                int decimales = configuration.path("decimales").asInt(0);
                BigDecimal largeur = max.subtract(min).divide(BigDecimal.valueOf(TRANCHES_PAR_DEFAUT), 10, RoundingMode.HALF_UP);
                for (int i = 1; i < TRANCHES_PAR_DEFAUT; i++) {
                    BigDecimal borne = StatistiqueServiceImpl.net(min.add(largeur.multiply(BigDecimal.valueOf(i))).setScale(decimales, RoundingMode.HALF_UP));
                    if (bornes.isEmpty() || borne.compareTo(bornes.get(bornes.size() - 1)) > 0) {
                        bornes.add(borne);
                    }
                }
            }
        }
        String granularite = config != null ? config.path("granularite").asText("MOIS") : "MOIS";
        return new StatistiqueRepository.Decoupage(question.getType(), bornes, granularite);
    }

    /** Valeurs possibles d'un critère, avec leur libellé. DATE : celles présentes dans les réponses retenues. */
    List<CritereModel.ValeurCritere> valeursCritere(Reference reference, Filtre filtre) {
        return switch (reference.question.getType()) {
            case CHOIX_UNIQUE -> reference.libellesOptions.entrySet().stream()
                    .map(e -> new CritereModel.ValeurCritere(e.getKey(), e.getValue()))
                    .toList();
            case OUI_NON -> List.of(new CritereModel.ValeurCritere("true", "Oui"),
                    new CritereModel.ValeurCritere("false", "Non"));
            case NUMERIQUE -> {
                List<BigDecimal> bornes = decoupage(reference).bornes();
                List<CritereModel.ValeurCritere> tranches = new ArrayList<>();
                for (int i = 0; i <= bornes.size(); i++) {
                    String libelle = i == 0 ? "Moins de " + texte(bornes.get(0))
                            : i == bornes.size() ? texte(bornes.get(i - 1)) + " et plus"
                            : texte(bornes.get(i - 1)) + " à moins de " + texte(bornes.get(i));
                    tranches.add(new CritereModel.ValeurCritere("t" + (i + 1), libelle));
                }
                yield tranches;
            }
            case DATE -> statistiqueRepository.valeursCritere(filtre, reference.ids, decoupage(reference)).stream()
                    .map(v -> new CritereModel.ValeurCritere(v, v))
                    .toList();
            default -> List.of();
        };
    }

    private static String texte(BigDecimal nombre) {
        return StatistiqueServiceImpl.net(nombre).toPlainString();
    }

    ZoneId fuseau() {
        return fuseau;
    }
}
