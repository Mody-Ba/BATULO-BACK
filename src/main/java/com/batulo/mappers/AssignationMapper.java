package com.batulo.mappers;

import com.batulo.service.model.AssignationModel;
import com.batulo.service.model.AssignationResponse;
import com.batulo.service.model.AuteurResponse;
import com.batulo.service.model.PageResponse;
import com.batulo.service.model.SondageGestionnaireModel;
import com.batulo.service.model.SondageGestionnaireResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mapper API : convertit uniquement Model -> Response.
 * Ne doit jamais connaître l'entité JPA Assignation.
 */
@Component("apiAssignationMapper")
public class AssignationMapper {

    public List<AssignationResponse> toResponses(List<AssignationModel> assignations) {
        return assignations.stream().map(this::toResponse).toList();
    }

    public PageResponse<SondageGestionnaireResponse> toResponse(Page<SondageGestionnaireModel> page) {
        return new PageResponse<>(
                page.getContent().stream()
                        .map(s -> new SondageGestionnaireResponse(s.id(), s.titre(), s.statut(), s.publie(),
                                s.version(), s.relation(), s.dateModification()))
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    private AssignationResponse toResponse(AssignationModel model) {
        return new AssignationResponse(
                model.gestionnaireId(),
                model.prenom(),
                model.nom(),
                model.courriel(),
                model.actif(),
                model.dateAssignation(),
                model.assignePar() == null ? null
                        : new AuteurResponse(model.assignePar().id(), model.assignePar().prenom(),
                        model.assignePar().nom()));
    }
}
