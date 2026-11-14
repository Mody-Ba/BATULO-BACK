package com.batulo.service;

import com.batulo.entity.Sondage;
import com.batulo.exception.ConflitException;
import com.batulo.exception.RegleGestionException;
import com.batulo.exception.ResourceNotFoundException;
import com.batulo.repository.ReponseRepository;
import com.batulo.repository.SondageRepository;
import com.batulo.service.mappers.ReponseMapper;
import com.batulo.service.model.LienSondageModel;
import com.batulo.service.model.ReponseModel;
import com.batulo.service.model.SuiviCollecteModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class SuiviCollecteServiceImpl implements SuiviCollecteService {

    private final AccesSondage accesSondage;
    private final ReponseRepository reponseRepository;
    private final SondageRepository sondageRepository;
    private final ReponseMapper reponseMapper;
    private final RegleCollecte regleCollecte;
    private final GenerateurQrCode generateurQrCode;

    /** Adresse de la page du formulaire répondant (frontend) ; le jeton y est ajouté. */
    @Value("${batulo.collecte.url-formulaire}")
    private String urlFormulaire;

    public SuiviCollecteServiceImpl(AccesSondage accesSondage, ReponseRepository reponseRepository,
                                    SondageRepository sondageRepository,
                                    ReponseMapper reponseMapper, RegleCollecte regleCollecte,
                                    GenerateurQrCode generateurQrCode) {
        this.accesSondage = accesSondage;
        this.reponseRepository = reponseRepository;
        this.sondageRepository = sondageRepository;
        this.reponseMapper = reponseMapper;
        this.regleCollecte = regleCollecte;
        this.generateurQrCode = generateurQrCode;
    }

    @Override
    public LienSondageModel lien(Long utilisateurId, Long sondageId) {
        Sondage version = sondageLisible(utilisateurId, sondageId);
        String jeton = version.getRacine().getJetonPublic();
        if (jeton == null) {
            throw new ConflitException("SONDAGE_BROUILLON",
                    "Ce sondage n'a jamais été publié : son lien sera créé à la publication.");
        }
        String base = urlFormulaire.endsWith("/") ? urlFormulaire : urlFormulaire + "/";
        return new LienSondageModel(jeton, base + jeton);
    }

    @Override
    public byte[] qrCode(Long utilisateurId, Long sondageId, int taille) {
        return generateurQrCode.png(lien(utilisateurId, sondageId).url(), taille);
    }

    @Override
    public SuiviCollecteModel suivi(Long utilisateurId, Long sondageId) {
        Sondage version = sondageLisible(utilisateurId, sondageId);
        Instant maintenant = Instant.now();
        Optional<String> raison = regleCollecte.raisonFermeture(version, maintenant);
        return new SuiviCollecteModel(
                version.getId(),
                version.getVersion(),
                RegleSondage.statut(version, maintenant),
                raison.isEmpty(),
                raison.orElse(null),
                reponseRepository.countBySondageId(version.getId()),
                reponseRepository.countByLigneeId(version.getIdLignee()),
                reponseRepository.derniereReponse(version.getId()));
    }

    @Override
    public Page<ReponseModel> reponses(Long utilisateurId, Long sondageId, boolean lignee, Integer version,
                                       int page, int taille) {
        if (lignee && version != null) {
            throw new RegleGestionException("PARAMETRE_INVALIDE",
                    "Choisissez lignee=true (toutes les versions) ou version=N, pas les deux.");
        }
        Sondage sondage = sondageLisible(utilisateurId, sondageId);
        PageRequest pageRequest = PageRequest.of(page, taille,
                Sort.by(Sort.Order.desc("dateSoumission"), Sort.Order.desc("id")));
        if (lignee) {
            return reponseRepository.findByLigneeId(sondage.getIdLignee(), pageRequest).map(reponseMapper::toModel);
        }
        // Visibilité par lignée : la version N est lisible dès que l'une des versions l'est.
        Long versionId = version == null ? sondage.getId()
                : sondageRepository.findLignee(sondage.getIdLignee()).stream()
                .filter(v -> v.getVersion() == version)
                .map(Sondage::getId)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("VERSION_INTROUVABLE",
                        "Ce sondage n'a pas de version " + version + "."));
        return reponseRepository.findBySondageId(versionId, pageRequest).map(reponseMapper::toModel);
    }

    private Sondage sondageLisible(Long utilisateurId, Long sondageId) {
        return accesSondage.sondageLisible(accesSondage.acteur(utilisateurId), sondageId);
    }
}
