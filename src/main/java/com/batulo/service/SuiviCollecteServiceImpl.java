package com.batulo.service;

import com.batulo.entity.Sondage;
import com.batulo.exception.ConflitException;
import com.batulo.repository.ReponseRepository;
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
    private final ReponseMapper reponseMapper;
    private final RegleCollecte regleCollecte;
    private final GenerateurQrCode generateurQrCode;

    /** Adresse de la page du formulaire répondant (frontend) ; le jeton y est ajouté. */
    @Value("${batulo.collecte.url-formulaire}")
    private String urlFormulaire;

    public SuiviCollecteServiceImpl(AccesSondage accesSondage, ReponseRepository reponseRepository,
                                    ReponseMapper reponseMapper, RegleCollecte regleCollecte,
                                    GenerateurQrCode generateurQrCode) {
        this.accesSondage = accesSondage;
        this.reponseRepository = reponseRepository;
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
    public Page<ReponseModel> reponses(Long utilisateurId, Long sondageId, int page, int taille) {
        Sondage version = sondageLisible(utilisateurId, sondageId);
        return reponseRepository.findBySondageId(version.getId(),
                        PageRequest.of(page, taille, Sort.by(Sort.Order.desc("dateSoumission"), Sort.Order.desc("id"))))
                .map(reponseMapper::toModel);
    }

    private Sondage sondageLisible(Long utilisateurId, Long sondageId) {
        return accesSondage.sondageLisible(accesSondage.acteur(utilisateurId), sondageId);
    }
}
