package com.batulo.service;

import com.batulo.entity.Invitation;
import com.batulo.entity.Sondage;
import com.batulo.entity.Utilisateur;
import com.batulo.enums.Langue;
import com.batulo.enums.SeparateurDecimal;
import com.batulo.enums.StatutInvitation;
import com.batulo.enums.TypeEnvoiInvitation;
import com.batulo.exception.ConflitException;
import com.batulo.exception.LimiteDepasseeException;
import com.batulo.exception.RegleGestionException;
import com.batulo.exception.ResourceNotFoundException;
import com.batulo.repository.InvitationEnvoiRepository;
import com.batulo.repository.InvitationRepository;
import com.batulo.repository.InvitationSpecifications;
import com.batulo.repository.SondageRepository;
import com.batulo.service.export.EcrivainCsv;
import com.batulo.service.mappers.InvitationMapper;
import com.batulo.service.model.ExportModel;
import com.batulo.service.model.InvitationInfoModel;
import com.batulo.service.model.InvitationModel;
import com.batulo.service.model.RapportEnvoiModel;
import com.batulo.service.model.RapportImportModel;
import com.batulo.service.model.ResumeInvitationsModel;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class InvitationServiceImpl implements InvitationService {

    private static final Logger log = LoggerFactory.getLogger(InvitationServiceImpl.class);

    /** Contrôle simple et prévisible : une arobase, un domaine avec un point, sans espace. */
    private static final Pattern COURRIEL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern TELEPHONE = Pattern.compile("^[+0-9 ().\\-]{3,30}$");
    private static final int COURRIEL_MAX = 254;
    private static final int NOM_MAX = 100;

    private final AccesSondage accesSondage;
    private final InvitationRepository invitationRepository;
    private final InvitationEnvoiRepository envoiRepository;
    private final SondageRepository sondageRepository;
    private final InvitationMapper invitationMapper;
    private final JetonsInvitation jetons;
    private final EnvoiInvitations envoiInvitations;
    private final RegleCollecte regleCollecte;
    private final int envoisParHeure;
    private final Duration delaiRelance;
    private final int invitesMax;
    private final int lignesImportMax;
    private final ZoneId fuseau;

    @PersistenceContext
    private EntityManager entityManager;

    public InvitationServiceImpl(AccesSondage accesSondage, InvitationRepository invitationRepository,
                                 InvitationEnvoiRepository envoiRepository, SondageRepository sondageRepository,
                                 InvitationMapper invitationMapper, JetonsInvitation jetons,
                                 EnvoiInvitations envoiInvitations, RegleCollecte regleCollecte,
                                 @Value("${batulo.invitations.envois-par-heure:500}") int envoisParHeure,
                                 @Value("${batulo.invitations.delai-relance-heures:24}") int delaiRelanceHeures,
                                 @Value("${batulo.invitations.invites-max:10000}") int invitesMax,
                                 @Value("${batulo.invitations.lignes-import-max:1000}") int lignesImportMax,
                                 @Value("${batulo.fuseau-horaire}") String fuseauHoraire) {
        this.accesSondage = accesSondage;
        this.invitationRepository = invitationRepository;
        this.envoiRepository = envoiRepository;
        this.sondageRepository = sondageRepository;
        this.invitationMapper = invitationMapper;
        this.jetons = jetons;
        this.envoiInvitations = envoiInvitations;
        this.regleCollecte = regleCollecte;
        this.envoisParHeure = envoisParHeure;
        this.delaiRelance = Duration.ofHours(delaiRelanceHeures);
        this.invitesMax = invitesMax;
        this.lignesImportMax = lignesImportMax;
        this.fuseau = ZoneId.of(fuseauHoraire);
    }

    // ---------------------------------------------------------------- Lecture

    @Override
    public Page<InvitationModel> lister(Long utilisateurId, Long sondageId, StatutInvitation statut, String recherche,
                                        int page, int taille) {
        Sondage racine = racine(utilisateurId, sondageId);
        String terme = recherche == null || recherche.isBlank() ? null : NormaliseurRecherche.normaliser(recherche);
        return invitationRepository.findAll(InvitationSpecifications.deLaLignee(racine.getId())
                        .and(InvitationSpecifications.parStatut(statut))
                        .and(InvitationSpecifications.recherche(terme)),
                PageRequest.of(page, taille, Sort.by(Sort.Order.asc("id"))))
                .map(invitationMapper::toModel);
    }

    @Override
    public ResumeInvitationsModel resume(Long utilisateurId, Long sondageId) {
        Sondage racine = racine(utilisateurId, sondageId);
        Map<StatutInvitation, Long> parStatut = invitationRepository.compterParStatut(racine.getId());
        long total = parStatut.values().stream().mapToLong(Long::longValue).sum();
        long envoyees = invitationRepository.countByLigneeIdAndPremierEnvoiLeIsNotNullAndRevoqueeLeIsNull(racine.getId());
        long repondues = parStatut.get(StatutInvitation.A_REPONDU);
        return new ResumeInvitationsModel(total, parStatut.get(StatutInvitation.A_INVITER),
                parStatut.get(StatutInvitation.INVITEE), parStatut.get(StatutInvitation.OUVERTE), repondues,
                parStatut.get(StatutInvitation.REVOQUEE), parStatut.get(StatutInvitation.EN_ECHEC), envoyees,
                StatistiqueServiceImpl.pourcentage(repondues, envoyees));
    }

    // ---------------------------------------------------------------- Liste des invités

    @Override
    @Transactional
    public InvitationModel ajouter(Long utilisateurId, Long sondageId, InvitationInfoModel info) {
        Utilisateur acteur = accesSondage.acteur(utilisateurId);
        Sondage racine = accesSondage.sondageLisible(acteur, sondageId).getRacine();
        InvitationInfoModel propre = nettoyer(info);
        erreur(propre).ifPresent(e -> {
            throw new RegleGestionException("INVITE_INVALIDE", e.message());
        });
        if (invitationRepository.existsByLigneeIdAndCourriel(racine.getId(), propre.courriel())) {
            throw new ConflitException("INVITE_EXISTANT", "Cette adresse est déjà invitée à ce sondage.");
        }
        if (invitationRepository.countByLigneeId(racine.getId()) >= invitesMax) {
            throw new RegleGestionException("LIMITE_ATTEINTE", "Un sondage ne peut pas avoir plus de " + invitesMax
                    + " invités.");
        }
        Invitation invitation = invitationRepository.saveAndFlush(nouvelle(racine, acteur, propre));
        log.info("Sondage {} : invitation {} ajoutée par le compte {}", racine.getId(), invitation.getId(), utilisateurId);
        return invitationMapper.toModel(invitation);
    }

    @Override
    @Transactional
    public InvitationModel modifier(Long utilisateurId, Long sondageId, Long invitationId, InvitationInfoModel info) {
        Sondage racine = racine(utilisateurId, sondageId);
        Invitation invitation = invitation(racine, invitationId);
        verifierModifiable(invitation);
        InvitationInfoModel propre = nettoyer(info);
        erreur(propre).ifPresent(e -> {
            throw new RegleGestionException("INVITE_INVALIDE", e.message());
        });
        if (!propre.courriel().equals(invitation.getCourriel())
                && invitationRepository.existsByLigneeIdAndCourriel(racine.getId(), propre.courriel())) {
            throw new ConflitException("INVITE_EXISTANT", "Cette adresse est déjà invitée à ce sondage.");
        }
        appliquer(invitation, propre);
        invitationRepository.flush();
        return invitationMapper.toModel(invitation);
    }

    @Override
    @Transactional
    public void supprimer(Long utilisateurId, Long sondageId, Long invitationId) {
        Sondage racine = racine(utilisateurId, sondageId);
        Invitation invitation = invitation(racine, invitationId);
        if (invitation.getReponduLe() != null) {
            throw dejaRepondu();
        }
        invitationRepository.delete(invitation);
        log.info("Sondage {} : invitation {} supprimée par le compte {}", racine.getId(), invitationId, utilisateurId);
    }

    @Override
    @Transactional
    public InvitationModel revoquer(Long utilisateurId, Long sondageId, Long invitationId) {
        Sondage racine = racine(utilisateurId, sondageId);
        Invitation invitation = invitation(racine, invitationId);
        if (invitation.getReponduLe() != null) {
            throw dejaRepondu();
        }
        if (invitation.getRevoqueeLe() == null) {
            invitation.setRevoqueeLe(Instant.now());
            log.info("Sondage {} : invitation {} révoquée par le compte {}", racine.getId(), invitationId, utilisateurId);
        }
        return invitationMapper.toModel(invitation);
    }

    // ---------------------------------------------------------------- Import et reprise

    @Override
    @Transactional
    public RapportImportModel importer(Long utilisateurId, Long sondageId, byte[] fichier) {
        Utilisateur acteur = accesSondage.acteur(utilisateurId);
        Sondage racine = accesSondage.sondageLisible(acteur, sondageId).getRacine();
        List<LecteurCsv.Enregistrement> enregistrements = LecteurCsv.lire(new String(fichier, StandardCharsets.UTF_8));
        if (enregistrements.isEmpty()) {
            throw new RegleGestionException("IMPORT_INVALIDE", "Le fichier est vide.");
        }
        Map<String, Integer> colonnes = colonnes(enregistrements.get(0).champs());
        if (!colonnes.containsKey("courriel")) {
            throw new RegleGestionException("IMPORT_INVALIDE", "La ligne d'en-tête doit contenir la colonne « courriel » "
                    + "(colonnes reconnues : prenom, nom, courriel, telephone, langue ; séparateur « , » ou « ; »).");
        }
        List<LecteurCsv.Enregistrement> lignes = enregistrements.subList(1, enregistrements.size());
        if (lignes.size() > lignesImportMax) {
            throw new RegleGestionException("IMPORT_TROP_GRAND", "Un import contient au plus " + lignesImportMax
                    + " lignes (ce fichier en a " + lignes.size() + ") : découpez-le.");
        }
        List<Candidat> candidats = new ArrayList<>();
        for (LecteurCsv.Enregistrement e : lignes) {
            Function<String, String> champ = nom -> {
                Integer rang = colonnes.get(nom);
                return rang == null || rang >= e.champs().size() ? null : e.champs().get(rang);
            };
            candidats.add(new Candidat(e.ligne(), champ.apply("prenom"), champ.apply("nom"), champ.apply("courriel"),
                    champ.apply("telephone"), champ.apply("langue"), null));
        }
        RapportImportModel rapport = enregistrer(racine, acteur, candidats);
        log.info("Sondage {} : import de {} ligne(s) par le compte {} ({} acceptée(s))", racine.getId(), lignes.size(),
                utilisateurId, rapport.acceptees());
        return rapport;
    }

    @Override
    @Transactional
    public RapportImportModel reprendre(Long utilisateurId, Long sondageId, Long sondageSourceId) {
        Utilisateur acteur = accesSondage.acteur(utilisateurId);
        Sondage racine = accesSondage.sondageLisible(acteur, sondageId).getRacine();
        Sondage source = accesSondage.sondageLisible(acteur, sondageSourceId).getRacine();
        if (source.getId().equals(racine.getId())) {
            throw new RegleGestionException("REPRISE_INVALIDE", "Choisissez un autre sondage que celui-ci.");
        }
        List<Candidat> candidats = new ArrayList<>();
        int rang = 0;
        for (Invitation i : invitationRepository.findByLigneeIdOrderByIdAsc(source.getId())) {
            candidats.add(new Candidat(++rang, i.getPrenom(), i.getNom(), i.getCourriel(), i.getTelephone(), null,
                    i.getLangue()));
        }
        RapportImportModel rapport = enregistrer(racine, acteur, candidats);
        log.info("Sondage {} : {} invité(s) repris du sondage {} par le compte {}", racine.getId(), rapport.acceptees(),
                source.getId(), utilisateurId);
        return rapport;
    }

    /** Une ligne à importer ; langue en texte (CSV) ou déjà lue (reprise). */
    private record Candidat(int ligne, String prenom, String nom, String courriel, String telephone,
                            String langueTexte, Langue langue) {
    }

    private RapportImportModel enregistrer(Sondage racine, Utilisateur acteur, List<Candidat> candidats) {
        Set<String> existants = new HashSet<>(invitationRepository.courriels(racine.getId()));
        Set<String> vus = new HashSet<>();
        long places = invitesMax - existants.size();
        List<RapportImportModel.LigneRefusee> refusees = new ArrayList<>();
        List<Invitation> nouvelles = new ArrayList<>();
        for (Candidat c : candidats) {
            String courrielBrut = c.courriel() == null ? null : c.courriel().trim();
            Langue langue = c.langue();
            if (langue == null && c.langueTexte() != null && !c.langueTexte().isBlank()) {
                langue = Langue.parCode(c.langueTexte()).orElse(null);
                if (langue == null) {
                    refusees.add(new RapportImportModel.LigneRefusee(c.ligne(), courrielBrut, "LANGUE_INVALIDE",
                            "Langue inconnue : « " + c.langueTexte().trim() + " » (fr, en ou ar)."));
                    continue;
                }
            }
            InvitationInfoModel info = nettoyer(new InvitationInfoModel(c.prenom(), c.nom(), courrielBrut,
                    c.telephone(), langue));
            Optional<Erreur> erreur = erreur(info);
            if (erreur.isPresent()) {
                refusees.add(new RapportImportModel.LigneRefusee(c.ligne(), courrielBrut, erreur.get().raison(),
                        erreur.get().message()));
            } else if (existants.contains(info.courriel())) {
                refusees.add(new RapportImportModel.LigneRefusee(c.ligne(), courrielBrut, "DEJA_INVITE",
                        "Cette adresse est déjà invitée à ce sondage."));
            } else if (!vus.add(info.courriel())) {
                refusees.add(new RapportImportModel.LigneRefusee(c.ligne(), courrielBrut, "DOUBLON_FICHIER",
                        "Cette adresse apparaît plus haut dans la liste."));
            } else if (nouvelles.size() >= places) {
                refusees.add(new RapportImportModel.LigneRefusee(c.ligne(), courrielBrut, "LIMITE_INVITES",
                        "Un sondage ne peut pas avoir plus de " + invitesMax + " invités."));
            } else {
                nouvelles.add(nouvelle(racine, acteur, info));
            }
        }
        invitationRepository.saveAll(nouvelles);
        invitationRepository.flush();
        return new RapportImportModel(nouvelles.size(), refusees);
    }

    /** Colonnes de l'en-tête (sans majuscules ni accents) -> rang ; alias usuels acceptés. */
    private static Map<String, Integer> colonnes(List<String> entetes) {
        Map<String, String> alias = Map.of("email", "courriel", "e-mail", "courriel", "mail", "courriel",
                "tel", "telephone", "phone", "telephone", "prénom", "prenom");
        Map<String, Integer> colonnes = new HashMap<>();
        for (int i = 0; i < entetes.size(); i++) {
            String nom = Normalizer.normalize(entetes.get(i).trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                    .toLowerCase(Locale.ROOT);
            colonnes.putIfAbsent(alias.getOrDefault(nom, nom), i);
        }
        return colonnes;
    }

    // ---------------------------------------------------------------- Envois

    @Override
    @Transactional
    public RapportEnvoiModel envoyer(Long utilisateurId, Long sondageId, List<Long> invitationIds) {
        return reserver(utilisateurId, sondageId, invitationIds, TypeEnvoiInvitation.INVITATION);
    }

    @Override
    @Transactional
    public RapportEnvoiModel relancer(Long utilisateurId, Long sondageId, List<Long> invitationIds) {
        return reserver(utilisateurId, sondageId, invitationIds, TypeEnvoiInvitation.RELANCE);
    }

    /**
     * Choisit les invitations concernées, vérifie la limite par heure de l'entreprise (envois de la dernière heure et
     * envois en file), les marque « en file » et planifie l'envoi après la validation. L'entreprise est verrouillée le
     * temps de la réservation : deux demandes simultanées ne dépassent pas la limite ni n'envoient deux fois.
     */
    private RapportEnvoiModel reserver(Long utilisateurId, Long sondageId, List<Long> invitationIds,
                                       TypeEnvoiInvitation type) {
        Sondage racine = racine(utilisateurId, sondageId);
        Instant maintenant = Instant.now();
        verifierEnvoiPossible(racine, maintenant);
        entityManager.lock(racine.getEntreprise(), LockModeType.PESSIMISTIC_WRITE);

        boolean selection = invitationIds != null && !invitationIds.isEmpty();
        // Sélection : dans l'ordre de la demande, un id d'un autre sondage est « introuvable » ; sinon toute la liste.
        List<Long> ordre;
        Map<Long, Invitation> parId;
        if (selection) {
            ordre = List.copyOf(new LinkedHashSet<>(invitationIds));
            parId = invitationRepository.findByLigneeIdAndIdIn(racine.getId(), ordre).stream()
                    .collect(Collectors.toMap(Invitation::getId, Function.identity()));
        } else {
            List<Invitation> toutes = invitationRepository.findByLigneeIdOrderByIdAsc(racine.getId());
            ordre = toutes.stream().map(Invitation::getId).toList();
            parId = toutes.stream().collect(Collectors.toMap(Invitation::getId, Function.identity()));
        }
        List<RapportEnvoiModel.Ignoree> ignorees = new ArrayList<>();
        List<Invitation> retenues = new ArrayList<>();
        for (Long invitationId : ordre) {
            Invitation i = parId.get(invitationId);
            if (i == null) {
                ignorees.add(new RapportEnvoiModel.Ignoree(invitationId, "INTROUVABLE"));
                continue;
            }
            String raison = raisonDIgnorer(i, type, maintenant);
            if (raison == null) {
                retenues.add(i);
            } else if (selection || "DELAI_RELANCE".equals(raison)) {
                // Sans sélection, seules les relances retardées sont signalées : les autres ne sont pas concernées.
                ignorees.add(new RapportEnvoiModel.Ignoree(i.getId(), raison));
            }
        }

        Long entrepriseId = racine.getEntreprise().getId();
        long utilises = envoiRepository.countByEntrepriseIdAndDateEnvoiAfter(entrepriseId, maintenant.minus(Duration.ofHours(1)))
                + invitationRepository.compterEnFile(entrepriseId);
        long restant = Math.max(0, envoisParHeure - utilises);
        if (retenues.size() > restant) {
            throw new LimiteDepasseeException("LIMITE_ENVOIS", "Limite de " + envoisParHeure + " courriels par heure "
                    + "atteinte pour votre entreprise : " + restant + " envoi(s) encore possible(s) maintenant, "
                    + retenues.size() + " demandé(s). Réessayez plus tard ou réduisez la sélection.");
        }
        retenues.forEach(i -> i.setEnvoiEnFileLe(maintenant));
        invitationRepository.flush();
        envoiInvitations.planifier(retenues.stream().map(Invitation::getId).toList(), type);
        log.info("Sondage {} : {} {} mise(s) en file par le compte {} ({} ignorée(s))", racine.getId(), retenues.size(),
                type == TypeEnvoiInvitation.RELANCE ? "relance(s)" : "invitation(s)", utilisateurId, ignorees.size());
        return new RapportEnvoiModel(retenues.size(), ignorees);
    }

    /** Null si l'invitation est concernée par cet envoi ; sinon la raison de l'ignorer. */
    private String raisonDIgnorer(Invitation i, TypeEnvoiInvitation type, Instant maintenant) {
        if (i.getRevoqueeLe() != null) {
            return "REVOQUEE";
        }
        if (i.getReponduLe() != null) {
            return "A_REPONDU";
        }
        if (i.getEnvoiEnFileLe() != null) {
            return "ENVOI_EN_COURS";
        }
        if (type == TypeEnvoiInvitation.INVITATION) {
            // Un premier envoi en échec n'a pas de premierEnvoiLe : on peut réessayer.
            return i.getPremierEnvoiLe() != null ? "DEJA_INVITEE" : null;
        }
        if (i.getPremierEnvoiLe() == null) {
            return "JAMAIS_INVITEE";
        }
        return i.getDernierEnvoiLe() != null && i.getDernierEnvoiLe().isAfter(maintenant.minus(delaiRelance))
                ? "DELAI_RELANCE" : null;
    }

    /** Envoi seulement vers une version ouverte ou planifiée, d'une entreprise qui peut collecter. */
    private void verifierEnvoiPossible(Sondage racine, Instant maintenant) {
        if (envoiInvitations.versionCible(racine.getId(), maintenant).isEmpty()) {
            boolean publie = sondageRepository.findLignee(racine.getId()).stream().anyMatch(Sondage::isPublie);
            throw new ConflitException("SONDAGE_NON_OUVERT", publie
                    ? "Ce sondage est terminé : publiez une nouvelle version pour inviter ou relancer."
                    : "Publiez le sondage avant d'envoyer les invitations (la liste peut être préparée dans le brouillon).");
        }
        if (!regleCollecte.entrepriseAutorisee(racine.getEntreprise(), maintenant)) {
            throw new ConflitException("SONDAGE_FERME",
                    "Votre entreprise ne peut pas collecter de réponses actuellement (abonnement ou compte inactif).");
        }
    }

    // ---------------------------------------------------------------- Export du suivi

    @Override
    public ExportModel exporter(Long utilisateurId, Long sondageId) {
        Sondage racine = racine(utilisateurId, sondageId);
        // Tout est copié ici, dans la transaction : aucune entité n'en sort.
        List<InvitationModel> invitations = invitationRepository.findByLigneeIdOrderByIdAsc(racine.getId()).stream()
                .map(invitationMapper::toModel).toList();
        String nom = "invites_" + slug(racine.getTitre()) + "_" + LocalDate.now(fuseau) + ".csv";
        log.info("Export du suivi des invitations du sondage {} par le compte {}", racine.getId(), utilisateurId);
        return new ExportModel(nom, ExportServiceImpl.TYPE_CSV, flux -> {
            try (EcrivainCsv csv = new EcrivainCsv(flux, SeparateurDecimal.VIRGULE)) {
                csv.entetes(List.of("Prénom", "Nom", "Courriel", "Téléphone", "Langue", "Statut", "Ajouté le",
                        "Premier envoi", "Dernier envoi", "Dernière relance", "Envois", "Échec d'envoi", "Ouvert le",
                        "Répondu le", "Révoqué le"));
                for (InvitationModel i : invitations) {
                    List<Object> ligne = new ArrayList<>();
                    ligne.add(i.prenom());
                    ligne.add(i.nom());
                    ligne.add(i.courriel());
                    ligne.add(i.telephone());
                    ligne.add(i.langue() == null ? null : i.langue().getCode());
                    ligne.add(i.statut().name());
                    ligne.add(local(i.dateCreation()));
                    ligne.add(local(i.premierEnvoiLe()));
                    ligne.add(local(i.dernierEnvoiLe()));
                    ligne.add(local(i.derniereRelanceLe()));
                    ligne.add(i.nombreEnvois());
                    ligne.add(local(i.echecEnvoiLe()));
                    ligne.add(local(i.ouverteLe()));
                    // Jour seulement : l'heure n'est connue qu'en mode nominatif, et n'apporte rien au suivi.
                    ligne.add(i.reponduLe() == null ? null : LocalDate.ofInstant(i.reponduLe(), fuseau));
                    ligne.add(local(i.revoqueeLe()));
                    csv.ligne(ligne);
                }
            }
        });
    }

    // ---------------------------------------------------------------- Outils

    private Sondage racine(Long utilisateurId, Long sondageId) {
        return accesSondage.sondageLisible(accesSondage.acteur(utilisateurId), sondageId).getRacine();
    }

    private Invitation invitation(Sondage racine, Long invitationId) {
        return invitationRepository.findByIdAndLigneeId(invitationId, racine.getId())
                .orElseThrow(() -> new ResourceNotFoundException("INVITATION_INTROUVABLE",
                        "Aucune invitation " + invitationId + " pour ce sondage."));
    }

    private static void verifierModifiable(Invitation invitation) {
        if (invitation.getReponduLe() != null) {
            throw dejaRepondu();
        }
        if (invitation.getRevoqueeLe() != null) {
            throw new ConflitException("INVITATION_REVOQUEE", "Cette invitation est révoquée : elle ne se modifie plus.");
        }
    }

    private static ConflitException dejaRepondu() {
        return new ConflitException("INVITE_A_REPONDU", "Cette personne a déjà répondu : son invitation est conservée.");
    }

    private Invitation nouvelle(Sondage racine, Utilisateur acteur, InvitationInfoModel info) {
        Invitation invitation = new Invitation();
        invitation.setLignee(racine);
        invitation.setCreePar(acteur);
        appliquer(invitation, info);
        String sel = jetons.nouveauSel();
        invitation.setJetonSel(sel);
        invitation.setJetonHash(jetons.hashDepuisSel(sel));
        return invitation;
    }

    private static void appliquer(Invitation invitation, InvitationInfoModel info) {
        invitation.setPrenom(info.prenom());
        invitation.setNom(info.nom());
        invitation.setCourriel(info.courriel());
        invitation.setTelephone(info.telephone());
        invitation.setLangue(info.langue());
    }

    /** Textes vides -> null, espaces retirés, courriel en minuscules. */
    private static InvitationInfoModel nettoyer(InvitationInfoModel info) {
        return new InvitationInfoModel(texte(info.prenom()), texte(info.nom()),
                info.courriel() == null || info.courriel().isBlank() ? null
                        : NormaliseurCourriel.normaliser(info.courriel()),
                texte(info.telephone()), info.langue());
    }

    private record Erreur(String raison, String message) {
    }

    private static Optional<Erreur> erreur(InvitationInfoModel info) {
        if (info.courriel() == null) {
            return Optional.of(new Erreur("COURRIEL_MANQUANT", "Le courriel est obligatoire."));
        }
        if (info.courriel().length() > COURRIEL_MAX || !COURRIEL.matcher(info.courriel()).matches()) {
            return Optional.of(new Erreur("COURRIEL_INVALIDE", "Adresse courriel invalide."));
        }
        if ((info.prenom() != null && info.prenom().length() > NOM_MAX)
                || (info.nom() != null && info.nom().length() > NOM_MAX)) {
            return Optional.of(new Erreur("TEXTE_TROP_LONG", "Le prénom et le nom font au plus " + NOM_MAX
                    + " caractères."));
        }
        if (info.telephone() != null && !TELEPHONE.matcher(info.telephone()).matches()) {
            return Optional.of(new Erreur("TELEPHONE_INVALIDE",
                    "Téléphone invalide : chiffres, espaces, +, (, ), . et - seulement (30 caractères au plus)."));
        }
        return Optional.empty();
    }

    private static String texte(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private LocalDateTime local(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, fuseau);
    }

    private static String slug(String titre) {
        String slug = Normalizer.normalize(titre, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > 60) {
            slug = slug.substring(0, 60).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "sondage" : slug;
    }
}
