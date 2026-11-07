package com.batulo.service.mappers;

import com.batulo.entity.Abonnement;
import com.batulo.entity.Entreprise;
import com.batulo.enums.StatutAbonnement;
import com.batulo.service.model.AbonnementModel;
import org.springframework.stereotype.Component;

/**
 * Mapper DB : convertit uniquement l'entité JPA Abonnement en AbonnementModel.
 * Ne doit jamais connaître AbonnementResponse. Le statut et les jours restants sont calculés par le service.
 */
@Component("dbAbonnementMapper")
public class AbonnementMapper {

    public AbonnementModel toModel(Abonnement entity, StatutAbonnement statut, Long joursRestants) {
        Entreprise entreprise = entity.getEntreprise();
        return new AbonnementModel(
                entity.getId(),
                entreprise.getId(),
                entreprise.getNom(),
                entity.getFormule(),
                statut,
                entity.getDateDebut(),
                entity.getDateExpiration(),
                joursRestants,
                entity.getMontant(),
                entity.getDevise());
    }
}
