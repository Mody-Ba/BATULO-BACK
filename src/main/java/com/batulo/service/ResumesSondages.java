package com.batulo.service;

import com.batulo.entity.Sondage;
import com.batulo.repository.AssignationRepository;
import com.batulo.repository.ReponseRepository;
import com.batulo.repository.SondageRepository;
import com.batulo.service.mappers.SondageMapper;
import com.batulo.service.model.GestionnaireAssigneModel;
import com.batulo.service.model.SondageResumeModel;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lignes de la liste des sondages : chaque compteur est calculé pour toute la page en une requête groupée
 * (contenu, réponses de la version et de la lignée, assignés, brouillon en préparation), jamais ligne par ligne.
 */
@Component
class ResumesSondages {

    private final SondageRepository sondageRepository;
    private final ReponseRepository reponseRepository;
    private final AssignationRepository assignationRepository;
    private final SondageMapper sondageMapper;

    ResumesSondages(SondageRepository sondageRepository, ReponseRepository reponseRepository,
                    AssignationRepository assignationRepository, SondageMapper sondageMapper) {
        this.sondageRepository = sondageRepository;
        this.reponseRepository = reponseRepository;
        this.assignationRepository = assignationRepository;
        this.sondageMapper = sondageMapper;
    }

    /** @param avecAssignes false (gestionnaire) : gestionnairesAssignes vaut null */
    Page<SondageResumeModel> resumer(Page<Sondage> sondages, Instant maintenant, boolean avecAssignes) {
        if (sondages.isEmpty()) {
            return sondages.map(s -> null);
        }
        List<Long> ids = sondages.map(Sondage::getId).toList();
        Set<Long> lignees = sondages.stream().map(Sondage::getIdLignee).collect(Collectors.toSet());

        Map<Long, long[]> contenu = sondageRepository.compterContenu(ids).stream()
                .collect(Collectors.toMap(l -> (Long) l[0], l -> new long[]{(Long) l[1], (Long) l[2]}));
        Map<Long, Long> reponses = compteurs(reponseRepository.compterParSondage(ids));
        Map<Long, Long> reponsesLignee = compteurs(reponseRepository.compterParLignee(lignees));
        Map<Long, List<GestionnaireAssigneModel>> assignes = avecAssignes ? assignes(lignees) : Map.of();
        // Brouillon de chaque lignée : [id, version] (au plus un par lignée).
        Map<Long, long[]> brouillons = sondageRepository.brouillonsDesLignees(lignees).stream()
                .collect(Collectors.toMap(l -> (Long) l[0], l -> new long[]{(Long) l[1], (Integer) l[2]}));

        return sondages.map(s -> {
            long[] nombres = contenu.getOrDefault(s.getId(), new long[]{0, 0});
            long[] brouillon = brouillons.get(s.getIdLignee());
            Long brouillonEnPreparationId = brouillon != null && brouillon[1] > s.getVersion() ? brouillon[0] : null;
            return sondageMapper.toResumeModel(s, maintenant, nombres[0], nombres[1],
                    reponses.getOrDefault(s.getId(), 0L), reponsesLignee.getOrDefault(s.getIdLignee(), 0L),
                    avecAssignes ? assignes.getOrDefault(s.getIdLignee(), List.of()) : null,
                    brouillonEnPreparationId);
        });
    }

    private Map<Long, List<GestionnaireAssigneModel>> assignes(Set<Long> lignees) {
        Map<Long, List<GestionnaireAssigneModel>> parLignee = new HashMap<>();
        for (Object[] ligne : assignationRepository.gestionnairesDesLignees(lignees)) {
            parLignee.computeIfAbsent((Long) ligne[0], cle -> new ArrayList<>()).add(new GestionnaireAssigneModel(
                    (Long) ligne[1], (String) ligne[2], (String) ligne[3], (Boolean) ligne[4]));
        }
        return parLignee;
    }

    private static Map<Long, Long> compteurs(List<Object[]> lignes) {
        return lignes.stream().collect(Collectors.toMap(l -> (Long) l[0], l -> (Long) l[1]));
    }
}
