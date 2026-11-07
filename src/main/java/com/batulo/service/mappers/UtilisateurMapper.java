package com.batulo.service.mappers;

import com.batulo.entity.Entreprise;
import com.batulo.entity.Utilisateur;
import com.batulo.service.model.UtilisateurModel;
import org.springframework.stereotype.Component;

/**
 * Mapper DB : convertit uniquement l'entité JPA Utilisateur en UtilisateurModel.
 * Ne doit jamais connaître UtilisateurResponse. Le hash du mot de passe n'est pas recopié.
 */
@Component("dbUtilisateurMapper")
public class UtilisateurMapper {

    public UtilisateurModel toModel(Utilisateur entity) {
        Entreprise entreprise = entity.getEntreprise();
        return new UtilisateurModel(
                entity.getId(),
                entity.getPrenom(),
                entity.getNom(),
                entity.getCourriel(),
                entity.getRole(),
                entreprise == null ? null : entreprise.getId(),
                entreprise == null ? null : entreprise.getNom(),
                entity.isActif(),
                entity.isMotDePasseTemporaire(),
                entity.getMotDePasseTemporaireExpireLe(),
                entity.isPeutCreerSondages(),
                entity.getDateCreation());
    }
}
