package com.batulo.service.mappers;

import com.batulo.entity.Abonnement;
import com.batulo.entity.Entreprise;
import com.batulo.entity.Paiement;
import com.batulo.enums.StatutAbonnement;
import com.batulo.service.model.AbonnementModel;
import com.batulo.service.model.PaiementModel;
import org.springframework.stereotype.Component;

/**
 * Mapper DB : convertit uniquement les entités JPA Abonnement et Paiement en modèles.
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

    public PaiementModel toModel(Paiement entity) {
        return new PaiementModel(entity.getId(), entity.getDatePaiement(), entity.getFormule(), entity.getMontant(),
                entity.getDevise());
    }
}
