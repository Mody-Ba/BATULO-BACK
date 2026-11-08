package com.batulo.service.mappers;

import com.batulo.entity.Assignation;
import com.batulo.entity.Sondage;
import com.batulo.entity.Utilisateur;
import com.batulo.enums.RelationSondage;
import com.batulo.service.RegleSondage;
import com.batulo.service.model.AssignationModel;
import com.batulo.service.model.AuteurModel;
import com.batulo.service.model.SondageGestionnaireModel;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Mapper DB : convertit uniquement les entités Assignation et Sondage en modèles d'assignation.
 * Ne doit jamais connaître les Response.
 */
@Component("dbAssignationMapper")
public class AssignationMapper {

    public AssignationModel toModel(Assignation entity) {
        Utilisateur gestionnaire = entity.getGestionnaire();
        Utilisateur admin = entity.getAssignePar();
        return new AssignationModel(
                gestionnaire.getId(),
                gestionnaire.getPrenom(),
                gestionnaire.getNom(),
                gestionnaire.getCourriel(),
                gestionnaire.isActif(),
                entity.getDateAssignation(),
                admin == null ? null : new AuteurModel(admin.getId(), admin.getPrenom(), admin.getNom()));
    }

    public SondageGestionnaireModel toModel(Sondage entity, RelationSondage relation, Instant maintenant) {
        return new SondageGestionnaireModel(
                entity.getId(),
                entity.getTitre(),
                RegleSondage.statut(entity, maintenant),
                entity.isPublie(),
                entity.getVersion(),
                relation,
                entity.getDateModification());
    }
}
