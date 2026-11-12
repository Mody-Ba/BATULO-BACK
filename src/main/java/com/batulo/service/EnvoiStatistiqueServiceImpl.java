package com.batulo.service;

import com.batulo.entity.Conversation;
import com.batulo.entity.EnvoiStatistique;
import com.batulo.entity.Sondage;
import com.batulo.entity.Utilisateur;
import com.batulo.enums.CoteConversation;
import com.batulo.enums.FormatExport;
import com.batulo.enums.GranulariteDate;
import com.batulo.enums.Role;
import com.batulo.enums.StatutSondage;
import com.batulo.enums.TypeMessage;
import com.batulo.enums.TypeNotification;
import com.batulo.exception.ConflitException;
import com.batulo.exception.ResourceNotFoundException;
import com.batulo.repository.EnvoiStatistiqueRepository;
import com.batulo.repository.SondageRepository;
import com.batulo.repository.UtilisateurRepository;
import com.batulo.service.AnalysePerimetre.Perimetre;
import com.batulo.service.model.CritereModel;
import com.batulo.service.model.EnvoiStatistiqueDemandeModel;
import com.batulo.service.model.EnvoiStatistiqueModel;
import com.batulo.service.model.ExportModel;
import com.batulo.service.model.StatistiquesModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class EnvoiStatistiqueServiceImpl implements EnvoiStatistiqueService {

    private static final Logger log = LoggerFactory.getLogger(EnvoiStatistiqueServiceImpl.class);

    private final AnalysePerimetre analyse;
    private final SondageRepository sondageRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final EnvoiStatistiqueRepository envoiRepository;
    private final StatistiqueService statistiqueService;
    private final ExportService exportService;
    private final Messagerie messagerie;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public EnvoiStatistiqueServiceImpl(AnalysePerimetre analyse, SondageRepository sondageRepository,
                                       UtilisateurRepository utilisateurRepository,
                                       EnvoiStatistiqueRepository envoiRepository,
                                       StatistiqueService statistiqueService, ExportService exportService,
                                       Messagerie messagerie, NotificationService notificationService,
                                       ObjectMapper objectMapper) {
        this.analyse = analyse;
        this.sondageRepository = sondageRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.envoiRepository = envoiRepository;
        this.statistiqueService = statistiqueService;
        this.exportService = exportService;
        this.messagerie = messagerie;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public EnvoiStatistiqueModel envoyer(Long gestionnaireId, Long sondageId, EnvoiStatistiqueDemandeModel demande) {
        // Droits (créateur ou assigné) et filtres validés comme pour GET /statistiques.
        Perimetre perimetre = analyse.perimetre(gestionnaireId, sondageId, demande.lignee(), demande.du(), demande.au(),
                demande.criteres());
        Sondage sondage = perimetre.sondage();
        Instant maintenant = Instant.now();
        List<Sondage> aVerifier = demande.lignee()
                ? sondageRepository.findLignee(sondage.getIdLignee()).stream().filter(Sondage::isPublie).toList()
                : List.of(sondage);
        if (aVerifier.isEmpty() || aVerifier.stream().anyMatch(v -> RegleSondage.statut(v, maintenant) != StatutSondage.TERMINE)) {
            throw new ConflitException("SONDAGE_NON_TERMINE", "Les statistiques ne s'envoient que pour un sondage "
                    + "terminé (RG-04)" + (demande.lignee() ? " : toutes ses versions doivent l'être." : "."));
        }
        Utilisateur gestionnaire = utilisateurRepository.findWithEntrepriseById(gestionnaireId).orElseThrow();
        Utilisateur admin = utilisateurRepository.findFirstByEntrepriseIdAndRole(gestionnaire.getEntreprise().getId(),
                Role.ADMIN).orElseThrow(() -> new ResourceNotFoundException("ADMIN_INTROUVABLE",
                "Votre entreprise n'a pas d'admin."));

        List<String> descriptions = descriptions(perimetre.criteres());
        EnvoiStatistique envoi = new EnvoiStatistique();
        envoi.setSondage(sondage);
        envoi.setLignee(demande.lignee());
        envoi.setFiltres(filtres(demande, descriptions));
        envoi.setGestionnaire(gestionnaire);
        envoi.setExpediteurNom(Messagerie.nom(gestionnaire));
        envoi.setAdmin(admin);
        envoi.setCommentaire(demande.commentaire() == null || demande.commentaire().isBlank()
                ? null : demande.commentaire().trim());
        envoi = envoiRepository.save(envoi);

        // Message automatique dans leur conversation, et notification dédiée (pas de « Nouveau message » en double).
        Conversation conversation = messagerie.conversation(admin, gestionnaire);
        messagerie.poster(conversation, gestionnaire, CoteConversation.GESTIONNAIRE, TypeMessage.STATISTIQUES,
                envoi.getId(), texteMessage(sondage, envoi, demande, descriptions), false);
        notificationService.notifier(admin.getId(), TypeNotification.STATISTIQUES_RECUES, "Statistiques reçues",
                envoi.getExpediteurNom() + " vous a envoyé les statistiques du sondage « " + sondage.getTitre() + " ».");
        log.info("Statistiques du sondage {} envoyées par le gestionnaire {} à l'admin {} (envoi {})",
                sondageId, gestionnaireId, admin.getId(), envoi.getId());
        return toModel(envoi);
    }

    @Override
    public Page<EnvoiStatistiqueModel> recues(Long adminId, int page, int taille) {
        return envoiRepository.findByAdminId(adminId, recentes(page, taille)).map(this::toModel);
    }

    @Override
    public Page<EnvoiStatistiqueModel> envoyees(Long gestionnaireId, int page, int taille) {
        return envoiRepository.findByGestionnaireId(gestionnaireId, recentes(page, taille)).map(this::toModel);
    }

    @Override
    @Transactional
    public StatistiquesModel statistiques(Long adminId, Long envoiId) {
        EnvoiStatistique envoi = envoiDeLAdmin(adminId, envoiId);
        JsonNode f = envoi.getFiltres();
        return statistiqueService.statistiques(adminId, envoi.getSondage().getId(), envoi.isLignee(), date(f, "du"),
                date(f, "au"), granularite(f), criteres(f));
    }

    @Override
    @Transactional
    public ExportModel pdf(Long adminId, Long envoiId) {
        EnvoiStatistique envoi = envoiDeLAdmin(adminId, envoiId);
        JsonNode f = envoi.getFiltres();
        return exportService.exporterStatistiques(adminId, envoi.getSondage().getId(), FormatExport.PDF,
                envoi.isLignee(), date(f, "du"), date(f, "au"), granularite(f), criteres(f));
    }

    /** L'envoi doit être adressé à cet admin ; il est alors marqué comme consulté. Sinon 404. */
    private EnvoiStatistique envoiDeLAdmin(Long adminId, Long envoiId) {
        EnvoiStatistique envoi = envoiRepository.findByIdAndAdminId(envoiId, adminId)
                .orElseThrow(() -> new ResourceNotFoundException("ENVOI_INTROUVABLE",
                        "Aucun envoi de statistiques " + envoiId + " pour vous."));
        if (envoi.getConsulteLe() == null) {
            envoi.setConsulteLe(Instant.now());
        }
        return envoi;
    }

    private EnvoiStatistiqueModel toModel(EnvoiStatistique e) {
        JsonNode f = e.getFiltres();
        List<String> descriptions = new ArrayList<>();
        f.path("descriptionCriteres").forEach(d -> descriptions.add(d.asText()));
        return new EnvoiStatistiqueModel(e.getId(), e.getSondage().getId(), e.getSondage().getTitre(),
                e.getSondage().getVersion(), e.isLignee(), date(f, "du"), date(f, "au"), granularite(f), descriptions,
                e.getGestionnaire() == null ? null : e.getGestionnaire().getId(), e.getExpediteurNom(),
                e.getCommentaire(), e.getDateEnvoi(), e.getConsulteLe());
    }

    /** {du, au, granularite, criteres: {cle: [valeurs]}, descriptionCriteres: [...]} */
    private ObjectNode filtres(EnvoiStatistiqueDemandeModel demande, List<String> descriptions) {
        ObjectNode filtres = objectMapper.createObjectNode();
        if (demande.du() != null) {
            filtres.put("du", demande.du().toString());
        }
        if (demande.au() != null) {
            filtres.put("au", demande.au().toString());
        }
        if (demande.granulariteDate() != null) {
            filtres.put("granularite", demande.granulariteDate().name());
        }
        ObjectNode criteres = filtres.putObject("criteres");
        demande.criteres().forEach((cle, valeurs) -> {
            ArrayNode liste = criteres.putArray(cle);
            valeurs.forEach(liste::add);
        });
        ArrayNode texte = filtres.putArray("descriptionCriteres");
        descriptions.forEach(texte::add);
        return filtres;
    }

    private static String texteMessage(Sondage sondage, EnvoiStatistique envoi, EnvoiStatistiqueDemandeModel d,
                                       List<String> descriptions) {
        StringBuilder texte = new StringBuilder("Statistiques du sondage « ").append(sondage.getTitre()).append(" » (")
                .append(d.lignee() ? "toutes les versions" : "version " + sondage.getVersion());
        if (d.du() != null || d.au() != null) {
            texte.append(", ").append(d.du() == null ? "jusqu'au " + d.au()
                    : d.au() == null ? "depuis le " + d.du() : "du " + d.du() + " au " + d.au());
        }
        if (!descriptions.isEmpty()) {
            texte.append(", ").append(String.join(" ; ", descriptions));
        }
        texte.append(").");
        if (envoi.getCommentaire() != null) {
            texte.append("\nCommentaire : ").append(envoi.getCommentaire());
        }
        return texte.toString();
    }

    private static List<String> descriptions(List<CritereModel> criteres) {
        return criteres.stream()
                .map(c -> c.libelle() + " : " + c.valeurs().stream().map(CritereModel.ValeurCritere::libelle)
                        .collect(Collectors.joining(", ")))
                .toList();
    }

    private static LocalDate date(JsonNode filtres, String cle) {
        return filtres.hasNonNull(cle) ? LocalDate.parse(filtres.get(cle).asText()) : null;
    }

    private static GranulariteDate granularite(JsonNode filtres) {
        return filtres.hasNonNull("granularite") ? GranulariteDate.valueOf(filtres.get("granularite").asText()) : null;
    }

    private static Map<String, List<String>> criteres(JsonNode filtres) {
        Map<String, List<String>> criteres = new LinkedHashMap<>();
        filtres.path("criteres").fields().forEachRemaining(e -> {
            List<String> valeurs = new ArrayList<>();
            e.getValue().forEach(v -> valeurs.add(v.asText()));
            criteres.put(e.getKey(), valeurs);
        });
        return criteres;
    }

    private static PageRequest recentes(int page, int taille) {
        return PageRequest.of(page, taille, Sort.by(Sort.Order.desc("dateEnvoi"), Sort.Order.desc("id")));
    }
}
