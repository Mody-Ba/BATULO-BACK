package com.batulo.service;

import com.batulo.entity.Sondage;
import com.batulo.enums.Langue;
import com.batulo.exception.RegleGestionException;
import com.batulo.repository.SondageRepository;
import com.batulo.service.mappers.SondageMapper;
import com.batulo.service.model.CompletudeLangueModel;
import com.batulo.service.model.LanguesModel;
import com.batulo.service.model.SondageModel;
import com.batulo.service.model.TraductionsLangueModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class TraductionServiceImpl implements TraductionService {

    private static final Logger log = LoggerFactory.getLogger(TraductionServiceImpl.class);

    static final String TRADUCTION_INVALIDE = "TRADUCTION_INVALIDE";
    /** Langues supplémentaires au plus (fr, en, ar : toutes les autres). */
    static final int SUPPLEMENTAIRES_MAX = Langue.values().length - 1;

    private final AccesSondage accesSondage;
    private final SondageRepository sondageRepository;
    private final SondageMapper sondageMapper;

    public TraductionServiceImpl(AccesSondage accesSondage, SondageRepository sondageRepository,
                                 SondageMapper sondageMapper) {
        this.accesSondage = accesSondage;
        this.sondageRepository = sondageRepository;
        this.sondageMapper = sondageMapper;
    }

    @Override
    @Transactional
    public SondageModel definirLangues(Long utilisateurId, Long sondageId, LanguesModel langues) {
        Sondage sondage = accesSondage.sondageModifiable(utilisateurId, sondageId);
        Langue principale = langues.languePrincipale();
        if (principale == null) {
            throw new RegleGestionException(Langues.CODE_INVALIDE, "La langue principale est obligatoire.");
        }
        List<Langue> supplementaires = langues.languesSupplementaires() == null ? List.of()
                : langues.languesSupplementaires();
        Set<Langue> vues = new HashSet<>();
        for (Langue langue : supplementaires) {
            if (langue == principale) {
                throw new RegleGestionException(Langues.CODE_INVALIDE,
                        "La langue principale (" + principale + ") ne peut pas être aussi une langue supplémentaire.");
            }
            if (!vues.add(langue)) {
                throw new RegleGestionException(Langues.CODE_INVALIDE, "La langue « " + langue + " » est en double.");
            }
        }
        if (supplementaires.size() > SUPPLEMENTAIRES_MAX) {
            throw new RegleGestionException(Langues.CODE_INVALIDE,
                    SUPPLEMENTAIRES_MAX + " langues supplémentaires au plus.");
        }
        sondage.setLanguePrincipale(principale);
        sondage.setLanguesSupplementaires(supplementaires);
        sondage.marquerModifie();
        sondageRepository.flush();
        log.info("Sondage {} : langues {} (principale {}) définies par le compte {}", sondageId,
                sondage.getLanguesDisponibles(), principale, utilisateurId);
        return sondageMapper.toModel(sondage, Instant.now());
    }

    @Override
    public List<CompletudeLangueModel> completude(Long utilisateurId, Long sondageId) {
        Sondage sondage = accesSondage.sondageLisible(accesSondage.acteur(utilisateurId), sondageId);
        List<TextesTraduisibles.Texte> textes = TextesTraduisibles.inventaire(sondage);
        return sondage.getLanguesSupplementaires().stream()
                .map(langue -> {
                    List<CompletudeLangueModel.Manquant> manquants = textes.stream()
                            .filter(t -> t.traduction(langue) == null)
                            .map(t -> new CompletudeLangueModel.Manquant(t.cle(), t.source()))
                            .toList();
                    int traduits = textes.size() - manquants.size();
                    return new CompletudeLangueModel(langue, traduits, textes.size(), manquants.isEmpty(), manquants);
                })
                .toList();
    }

    @Override
    public TraductionsLangueModel traductions(Long utilisateurId, Long sondageId, Langue langue) {
        Sondage sondage = accesSondage.sondageLisible(accesSondage.acteur(utilisateurId), sondageId);
        verifierLangue(sondage, langue);
        return modele(sondage, langue);
    }

    @Override
    @Transactional
    public TraductionsLangueModel enregistrer(Long utilisateurId, Long sondageId, Langue langue,
                                              Map<String, String> textes) {
        Sondage sondage = accesSondage.sondageModifiable(utilisateurId, sondageId);
        verifierLangue(sondage, langue);
        Map<String, TextesTraduisibles.Texte> parCle = TextesTraduisibles.inventaire(sondage).stream()
                .collect(Collectors.toMap(TextesTraduisibles.Texte::cle, Function.identity(), (a, b) -> a,
                        LinkedHashMap::new));
        // Tout est contrôlé avant d'écrire : une requête invalide ne laisse rien à moitié enregistré.
        for (Map.Entry<String, String> e : textes.entrySet()) {
            TextesTraduisibles.Texte texte = parCle.get(e.getKey());
            if (texte == null) {
                throw new RegleGestionException(TRADUCTION_INVALIDE, "Clé inconnue : « " + e.getKey()
                        + " » (voir GET /api/sondages/" + sondageId + "/traductions/" + langue + ").");
            }
            String valeur = e.getValue() == null ? null : e.getValue().trim();
            if (valeur != null && valeur.length() > texte.longueurMax()) {
                throw new RegleGestionException(TRADUCTION_INVALIDE, "La traduction de « " + e.getKey()
                        + " » dépasse " + texte.longueurMax() + " caractères.");
            }
        }
        textes.forEach((cle, valeur) -> {
            String nette = valeur == null || valeur.isBlank() ? null : valeur.trim();
            parCle.get(cle).ecrire(langue, nette);
        });
        sondage.marquerModifie();
        sondageRepository.flush();
        log.info("Sondage {} : {} traduction(s) en {} enregistrée(s) par le compte {}", sondageId, textes.size(),
                langue, utilisateurId);
        return modele(sondage, langue);
    }

    private static void verifierLangue(Sondage sondage, Langue langue) {
        if (langue == null || !sondage.getLanguesSupplementaires().contains(langue)) {
            throw new RegleGestionException(Langues.CODE_INVALIDE, "« " + langue + " » n'est pas une langue "
                    + "supplémentaire de ce sondage (langues : " + sondage.getLanguesSupplementaires().stream()
                    .map(Langue::getCode).collect(Collectors.joining(", ")) + ").");
        }
    }

    private static TraductionsLangueModel modele(Sondage sondage, Langue langue) {
        List<TraductionsLangueModel.Texte> textes = TextesTraduisibles.inventaire(sondage).stream()
                .map(t -> new TraductionsLangueModel.Texte(t.cle(), t.element(), t.champ(), t.sectionId(),
                        t.questionId(), t.optionId(), t.source(), t.traduction(langue), t.longueurMax()))
                .toList();
        int traduits = (int) textes.stream().filter(t -> t.traduction() != null).count();
        return new TraductionsLangueModel(sondage.getId(), langue, sondage.getLanguePrincipale(), traduits,
                textes.size(), traduits == textes.size(), textes);
    }
}
