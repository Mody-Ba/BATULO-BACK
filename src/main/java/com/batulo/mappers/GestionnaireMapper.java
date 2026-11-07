package com.batulo.mappers;

import com.batulo.service.model.CreationGestionnaireModel;
import com.batulo.service.model.CreationGestionnaireRequest;
import com.batulo.service.model.ModificationGestionnaireModel;
import com.batulo.service.model.ModificationGestionnaireRequest;
import com.batulo.service.model.PageResponse;
import com.batulo.service.model.UtilisateurModel;
import com.batulo.service.model.UtilisateurResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Mapper API : convertit uniquement Request -> Model et Model -> Response.
 * Ne doit jamais connaître l'entité JPA Utilisateur.
 */
@Component("apiGestionnaireMapper")
public class GestionnaireMapper {

    private final AuthMapper authMapper;

    public GestionnaireMapper(AuthMapper authMapper) {
        this.authMapper = authMapper;
    }

    public CreationGestionnaireModel toModel(CreationGestionnaireRequest req) {
        return new CreationGestionnaireModel(req.prenom(), req.nom(), req.courriel());
    }

    public ModificationGestionnaireModel toModel(ModificationGestionnaireRequest req) {
        return new ModificationGestionnaireModel(req.prenom(), req.nom(), req.courriel());
    }

    public UtilisateurResponse toResponse(UtilisateurModel model) {
        return authMapper.toResponse(model);
    }

    public PageResponse<UtilisateurResponse> toResponse(Page<UtilisateurModel> page) {
        return new PageResponse<>(
                page.getContent().stream().map(authMapper::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
