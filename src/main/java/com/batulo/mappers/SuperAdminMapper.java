package com.batulo.mappers;

import com.batulo.service.model.AbonnementAdminModel;
import com.batulo.service.model.AbonnementAdminResponse;
import com.batulo.service.model.AdminDetailModel;
import com.batulo.service.model.AdminDetailResponse;
import com.batulo.service.model.AdminLigneModel;
import com.batulo.service.model.AdminLigneResponse;
import com.batulo.service.model.CreationAdminModel;
import com.batulo.service.model.CreationAdminRequest;
import com.batulo.service.model.ModificationAdminModel;
import com.batulo.service.model.ModificationAdminRequest;
import com.batulo.service.model.MotDePasseTemporaireModel;
import com.batulo.service.model.MotDePasseTemporaireResponse;
import com.batulo.service.model.PageResponse;
import com.batulo.service.model.TableauDeBordSuperAdminModel;
import com.batulo.service.model.TableauDeBordSuperAdminResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * Mapper API : convertit uniquement Request -> Model et Model -> Response.
 * Ne doit jamais connaître les entités JPA.
 */
@Component("apiSuperAdminMapper")
public class SuperAdminMapper {

    private final AuthMapper authMapper;
    private final AbonnementMapper abonnementMapper;

    public SuperAdminMapper(AuthMapper authMapper, AbonnementMapper abonnementMapper) {
        this.authMapper = authMapper;
        this.abonnementMapper = abonnementMapper;
    }

    public CreationAdminModel toModel(CreationAdminRequest req) {
        return new CreationAdminModel(req.prenom(), req.nom(), req.courriel(), req.nomEntreprise());
    }

    public ModificationAdminModel toModel(ModificationAdminRequest req) {
        return new ModificationAdminModel(req.prenom(), req.nom(), req.courriel(), req.nomEntreprise());
    }

    public AdminDetailResponse toResponse(AdminDetailModel model) {
        return new AdminDetailResponse(
                authMapper.toResponse(model.admin()),
                model.abonnement() == null ? null : abonnementMapper.toResponse(model.abonnement()),
                model.nombreGestionnaires());
    }

    public AbonnementAdminResponse toResponse(AbonnementAdminModel model) {
        return new AbonnementAdminResponse(
                abonnementMapper.toResponse(model.abonnement()),
                model.adminId(),
                model.adminPrenom(),
                model.adminNom(),
                model.adminCourriel());
    }

    public AdminLigneResponse toResponse(AdminLigneModel model) {
        AdminLigneModel.Entreprise entreprise = model.entreprise();
        AdminLigneModel.Admin admin = model.admin();
        AdminLigneModel.Abonnement abonnement = model.abonnement();
        return new AdminLigneResponse(
                new AdminLigneResponse.Entreprise(entreprise.id(), entreprise.nom(), entreprise.actif()),
                new AdminLigneResponse.Admin(admin.id(), admin.prenom(), admin.nom(), admin.courriel(), admin.actif(),
                        admin.motDePasseTemporaire(), admin.motDePasseTemporaireExpireLe(), admin.dateCreation()),
                abonnement == null ? null : new AdminLigneResponse.Abonnement(abonnement.formule(),
                        abonnement.statut(), abonnement.dateExpiration(), abonnement.joursRestants()),
                model.nombreGestionnaires());
    }

    public TableauDeBordSuperAdminResponse toResponse(TableauDeBordSuperAdminModel model) {
        TableauDeBordSuperAdminModel.Entreprises e = model.entreprises();
        return new TableauDeBordSuperAdminResponse(
                new TableauDeBordSuperAdminResponse.Entreprises(e.total(), e.actif(), e.expireBientot(), e.expire(),
                        e.enAttentePaiement(), e.desactive()),
                model.demandesAugmentationEnAttente());
    }

    public MotDePasseTemporaireResponse toResponse(MotDePasseTemporaireModel model) {
        return new MotDePasseTemporaireResponse(
                "Un nouveau mot de passe temporaire a été généré pour " + model.courriel()
                        + " (valide 24 h). L'ancien mot de passe ne fonctionne plus.",
                model.adminId(),
                model.courriel(),
                model.expireLe());
    }

    public <M, R> PageResponse<R> toResponse(Page<M> page, Function<M, R> conversion) {
        return new PageResponse<>(
                page.getContent().stream().map(conversion).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
