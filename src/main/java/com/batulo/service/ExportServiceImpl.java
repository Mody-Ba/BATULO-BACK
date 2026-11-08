package com.batulo.service;

import com.batulo.entity.Sondage;
import com.batulo.enums.FormatExport;
import com.batulo.enums.GranulariteDate;
import com.batulo.enums.SeparateurDecimal;
import com.batulo.enums.TypeQuestion;
import com.batulo.enums.ValeursExport;
import com.batulo.exception.RegleGestionException;
import com.batulo.repository.ExportRepository;
import com.batulo.repository.ExportRepository.LigneBrute;
import com.batulo.repository.StatistiqueRepository.Filtre;
import com.batulo.service.AnalysePerimetre.Perimetre;
import com.batulo.service.AnalysePerimetre.Reference;
import com.batulo.service.export.EcrivainCsv;
import com.batulo.service.export.EcrivainTableau;
import com.batulo.service.export.EcrivainXlsx;
import com.batulo.service.export.InfosRapport;
import com.batulo.service.export.RapportPdf;
import com.batulo.service.export.RapportXlsx;
import com.batulo.service.model.CritereModel;
import com.batulo.service.model.CroisementModel;
import com.batulo.service.model.ExportModel;
import com.batulo.service.model.StatistiquesModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ExportServiceImpl implements ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportServiceImpl.class);

    static final String TYPE_CSV = "text/csv;charset=UTF-8";
    static final String TYPE_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final String TYPE_PDF = "application/pdf";
    private static final int LONGUEUR_NOM = 60;
    private static final DateTimeFormatter GENERE_LE =
            DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH 'h' mm", Locale.CANADA_FRENCH);

    private final AnalysePerimetre analyse;
    private final AccesSondage accesSondage;
    private final StatistiqueService statistiqueService;
    private final ExportRepository exportRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate lectureSeule;
    private final int effectifMinimum;

    public ExportServiceImpl(AnalysePerimetre analyse, AccesSondage accesSondage, StatistiqueService statistiqueService,
                             ExportRepository exportRepository, ObjectMapper objectMapper,
                             PlatformTransactionManager transactionManager,
                             @Value("${batulo.statistiques.effectif-minimum:5}") int effectifMinimum) {
        this.analyse = analyse;
        this.accesSondage = accesSondage;
        this.statistiqueService = statistiqueService;
        this.exportRepository = exportRepository;
        this.objectMapper = objectMapper;
        this.lectureSeule = new TransactionTemplate(transactionManager);
        this.lectureSeule.setReadOnly(true);
        this.effectifMinimum = effectifMinimum;
    }

    // ---------------------------------------------------------------- Réponses brutes

    /** Une colonne du fichier : une question, ou une option (choix multiple, classement) ou ligne (matrice). */
    private record Colonne(String cle, TypeQuestion type, String sous, Map<String, String> libellesOptions) {
    }

    @Override
    public ExportModel exporterReponses(Long utilisateurId, Long sondageId, FormatExport format, ValeursExport valeurs,
                                        SeparateurDecimal separateurDecimal, boolean lignee, LocalDate du,
                                        LocalDate au, Map<String, List<String>> criteres) {
        if (format == FormatExport.PDF) {
            throw new RegleGestionException("FORMAT_INVALIDE", "Les réponses s'exportent en CSV ou en XLSX.");
        }
        Perimetre perimetre = analyse.perimetre(utilisateurId, sondageId, lignee, du, au, criteres);

        // Tout ce dont l'écriture a besoin est copié ici, dans la transaction : aucune entité n'en sort.
        List<String> entetes = new ArrayList<>(List.of("Id", "Date", "Version"));
        List<Colonne> colonnes = new ArrayList<>();
        Set<Long> questions = new LinkedHashSet<>();
        for (Map.Entry<String, Reference> e : perimetre.references().entrySet()) {
            Reference r = e.getValue();
            String libelle = r.question.getLibelle();
            Map<String, String> options = Map.copyOf(r.libellesOptions);
            questions.addAll(r.ids);
            switch (r.question.getType()) {
                case CHOIX_MULTIPLE, CLASSEMENT -> r.libellesOptions.forEach((valeur, libelleOption) -> {
                    entetes.add(libelle + " – " + libelleOption);
                    colonnes.add(new Colonne(e.getKey(), r.question.getType(), valeur, options));
                });
                case MATRICE -> r.question.getConfiguration().path("lignes").forEach(ligne -> {
                    entetes.add(libelle + " – " + ligne.asText());
                    colonnes.add(new Colonne(e.getKey(), TypeQuestion.MATRICE, ligne.asText(), options));
                });
                default -> {
                    entetes.add(libelle);
                    colonnes.add(new Colonne(e.getKey(), r.question.getType(), null, options));
                }
            }
        }
        List<String> entetesUniques = dedoublonner(entetes);
        Filtre filtre = perimetre.filtre();
        ZoneId fuseau = analyse.fuseau();
        String nom = nomFichier(perimetre.sondage(), lignee, "", format == FormatExport.CSV ? "csv" : "xlsx");
        journaliser("réponses " + format, utilisateurId, sondageId, lignee, du, au, perimetre.criteres());

        return new ExportModel(nom, format == FormatExport.CSV ? TYPE_CSV : TYPE_XLSX, flux -> {
            try (EcrivainTableau ecrivain = format == FormatExport.CSV
                    ? new EcrivainCsv(flux, separateurDecimal) : new EcrivainXlsx(flux, "Réponses")) {
                ecrivain.entetes(entetesUniques);
                Assembleur assembleur = new Assembleur(colonnes, valeurs, fuseau, ecrivain);
                lectureSeule.executeWithoutResult(statut ->
                        exportRepository.parcourir(filtre, questions, assembleur::ajouter));
                assembleur.terminer();
                log.info("Export des réponses du sondage {} : {} ligne(s) écrite(s)", sondageId, assembleur.lignes);
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        });
    }

    /**
     * Regroupe les lignes SQL d'une même réponse (elles se suivent, triées par réponse) et écrit une ligne de fichier
     * dès que la réponse suivante commence : une seule réponse en mémoire à la fois.
     */
    private final class Assembleur {

        private final List<Colonne> colonnes;
        private final ValeursExport valeurs;
        private final ZoneId fuseau;
        private final EcrivainTableau ecrivain;
        private Long reponseId;
        private Instant date;
        private int version;
        private final Map<String, List<LigneBrute>> parCle = new HashMap<>();
        private final Map<String, JsonNode> jsonParCle = new HashMap<>();
        private long lignes;

        Assembleur(List<Colonne> colonnes, ValeursExport valeurs, ZoneId fuseau, EcrivainTableau ecrivain) {
            this.colonnes = colonnes;
            this.valeurs = valeurs;
            this.fuseau = fuseau;
            this.ecrivain = ecrivain;
        }

        void ajouter(LigneBrute ligne) {
            if (reponseId != null && reponseId != ligne.reponseId()) {
                ecrire();
            }
            reponseId = ligne.reponseId();
            date = ligne.dateSoumission();
            version = ligne.version();
            if (ligne.cle() != null) {
                parCle.computeIfAbsent(ligne.cle(), c -> new ArrayList<>()).add(ligne);
            }
        }

        void terminer() {
            if (reponseId != null) {
                ecrire();
            }
        }

        private void ecrire() {
            List<Object> cellules = new ArrayList<>(colonnes.size() + 3);
            cellules.add(reponseId);
            cellules.add(LocalDateTime.ofInstant(date, fuseau));
            cellules.add(version);
            for (Colonne colonne : colonnes) {
                cellules.add(valeur(colonne, parCle.getOrDefault(colonne.cle(), List.of())));
            }
            try {
                ecrivain.ligne(cellules);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            lignes++;
            parCle.clear();
            jsonParCle.clear();
        }

        private Object valeur(Colonne colonne, List<LigneBrute> lignesQuestion) {
            if (lignesQuestion.isEmpty()) {
                return null;
            }
            LigneBrute premiere = lignesQuestion.get(0);
            boolean codes = valeurs == ValeursExport.CODES;
            return switch (colonne.type()) {
                case CHOIX_UNIQUE -> codes ? premiere.valeurOption()
                        : colonne.libellesOptions().getOrDefault(premiere.valeurOption(), premiere.valeurOption());
                case CHOIX_MULTIPLE -> lignesQuestion.stream().anyMatch(l -> colonne.sous().equals(l.valeurOption())) ? 1 : 0;
                case OUI_NON -> premiere.valeurBooleenne() == null ? null : codes
                        ? (premiere.valeurBooleenne() ? 1 : 0) : (premiere.valeurBooleenne() ? "Oui" : "Non");
                case ECHELLE, NUMERIQUE -> StatistiqueServiceImpl.net(premiere.valeurNumerique());
                case DATE -> premiere.valeurDate();
                case TEXTE_COURT, TEXTE_LONG -> premiere.valeurTexte();
                case CLASSEMENT -> {
                    JsonNode ordre = json(colonne.cle(), premiere);
                    for (int i = 0; i < ordre.size(); i++) {
                        if (colonne.sous().equals(ordre.get(i).asText())) {
                            yield i + 1;
                        }
                    }
                    yield null;
                }
                case MATRICE -> {
                    JsonNode choix = json(colonne.cle(), premiere).get(colonne.sous());
                    yield choix == null ? null : choix.asText();
                }
            };
        }

        /** JSON du classement ou de la matrice, lu une seule fois par réponse. */
        private JsonNode json(String cle, LigneBrute ligne) {
            return jsonParCle.computeIfAbsent(cle, c -> {
                try {
                    return objectMapper.readTree(ligne.valeurJson());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    // ---------------------------------------------------------------- Rapports

    @Override
    public ExportModel exporterStatistiques(Long utilisateurId, Long sondageId, FormatExport format, boolean lignee,
                                            LocalDate du, LocalDate au, GranulariteDate granulariteDate,
                                            Map<String, List<String>> criteres) {
        verifierFormatRapport(format);
        StatistiquesModel stats = statistiqueService.statistiques(utilisateurId, sondageId, lignee, du, au,
                granulariteDate, criteres);
        Sondage sondage = accesSondage.sondageLisible(accesSondage.acteur(utilisateurId), sondageId);
        InfosRapport infos = infos(sondage, stats.versions(), lignee, du, au, stats.criteres());
        journaliser("statistiques " + format, utilisateurId, sondageId, lignee, du, au, stats.criteres());
        String nom = nomFichier(sondage, lignee, "_statistiques", extension(format));
        return new ExportModel(nom, type(format), flux -> {
            if (format == FormatExport.PDF) {
                new RapportPdf(infos).statistiques(stats, flux);
            } else {
                new RapportXlsx(infos).statistiques(stats, flux);
            }
        });
    }

    @Override
    public ExportModel exporterCroisement(Long utilisateurId, Long sondageId, FormatExport format, String cleQuestion,
                                          String clePar, boolean lignee, LocalDate du, LocalDate au,
                                          Map<String, List<String>> criteres) {
        verifierFormatRapport(format);
        CroisementModel tableau = statistiqueService.croisement(utilisateurId, sondageId, cleQuestion, clePar, lignee,
                du, au, criteres);
        Perimetre perimetre = analyse.perimetre(utilisateurId, sondageId, lignee, du, au, Map.of());
        InfosRapport infos = infos(perimetre.sondage(), perimetre.versions().stream().map(Sondage::getVersion).toList(),
                lignee, du, au, tableau.criteres());
        journaliser("croisement " + format, utilisateurId, sondageId, lignee, du, au, tableau.criteres());
        String nom = nomFichier(perimetre.sondage(), lignee, "_croisement", extension(format));
        return new ExportModel(nom, type(format), flux -> {
            if (format == FormatExport.PDF) {
                new RapportPdf(infos).croisement(tableau, flux);
            } else {
                new RapportXlsx(infos).croisement(tableau, flux);
            }
        });
    }

    // ---------------------------------------------------------------- Outils

    private InfosRapport infos(Sondage sondage, List<Integer> versions, boolean lignee, LocalDate du, LocalDate au,
                               List<CritereModel> criteres) {
        String perimetre = lignee
                ? "Toutes les versions (" + versions.stream().map(String::valueOf).collect(Collectors.joining(", ")) + ")"
                : "Version " + sondage.getVersion();
        String periode = du == null && au == null ? "Toutes les dates"
                : du == null ? "Jusqu'au " + au : au == null ? "Depuis le " + du : "Du " + du + " au " + au;
        List<String> texteCriteres = criteres.stream()
                .map(c -> c.libelle() + " : " + c.valeurs().stream().map(CritereModel.ValeurCritere::libelle)
                        .collect(Collectors.joining(", ")))
                .toList();
        return new InfosRapport(sondage.getTitre(), sondage.getEntreprise().getNom(), perimetre, periode, texteCriteres,
                GENERE_LE.format(LocalDateTime.now(analyse.fuseau())), effectifMinimum);
    }

    /** « titre-du-sondage_v2_statistiques_2026-11-08.pdf » : sans accents, minuscules, tirets, 60 caractères au plus. */
    private String nomFichier(Sondage sondage, boolean lignee, String suffixe, String extension) {
        String titre = Normalizer.normalize(sondage.getTitre(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (titre.length() > LONGUEUR_NOM) {
            titre = titre.substring(0, LONGUEUR_NOM).replaceAll("-+$", "");
        }
        if (titre.isEmpty()) {
            titre = "sondage";
        }
        return titre + (lignee ? "_toutes-versions" : "_v" + sondage.getVersion()) + suffixe + "_"
                + LocalDate.now(analyse.fuseau()) + "." + extension;
    }

    /** Deux colonnes de même en-tête : la seconde devient « Libellé (2) ». */
    private static List<String> dedoublonner(List<String> entetes) {
        Set<String> vus = new HashSet<>();
        List<String> resultat = new ArrayList<>();
        for (String entete : entetes) {
            String unique = entete;
            for (int i = 2; !vus.add(unique); i++) {
                unique = entete + " (" + i + ")";
            }
            resultat.add(unique);
        }
        return resultat;
    }

    private static void verifierFormatRapport(FormatExport format) {
        if (format == FormatExport.CSV) {
            throw new RegleGestionException("FORMAT_INVALIDE", "Les rapports s'exportent en PDF ou en XLSX.");
        }
    }

    private static String extension(FormatExport format) {
        return format == FormatExport.PDF ? "pdf" : "xlsx";
    }

    private static String type(FormatExport format) {
        return format == FormatExport.PDF ? TYPE_PDF : TYPE_XLSX;
    }

    /** Trace de l'export : qui, quoi, quels filtres ; jamais de contenu de réponse. */
    private static void journaliser(String quoi, Long utilisateurId, Long sondageId, boolean lignee, LocalDate du,
                                    LocalDate au, List<CritereModel> criteres) {
        log.info("Export {} du sondage {} par le compte {} (lignée : {}, du {} au {}, {} critère(s))",
                quoi, sondageId, utilisateurId, lignee, du, au, criteres.size());
    }
}
