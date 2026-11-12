package com.batulo.controller;

import com.batulo.enums.StatutAbonnement;
import com.batulo.mappers.SuperAdminMapper;
import com.batulo.service.SuperAdminService;
import com.batulo.service.model.AbonnementAdminResponse;
import com.batulo.service.model.AdminDetailResponse;
import com.batulo.service.model.AdminLigneResponse;
import com.batulo.service.model.CreationAdminRequest;
import com.batulo.service.model.ModificationAdminRequest;
import com.batulo.service.model.MotDePasseTemporaireResponse;
import com.batulo.service.model.PageResponse;
import com.batulo.service.model.TableauDeBordSuperAdminResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Espace du super admin (SA-01 à SA-06), réservé au rôle SUPER_ADMIN.
 * Le compte du super admin n'est jamais modifiable par ces routes.
 */
@RestController
@RequestMapping("/api/super-admin")
@Tag(name = "Super admin", description = "Comptes admin et suivi des abonnements (SA-01 à SA-06)")
public class SuperAdminController {

    private final SuperAdminService superAdminService;
    private final SuperAdminMapper superAdminMapper;

    public SuperAdminController(SuperAdminService superAdminService, SuperAdminMapper superAdminMapper) {
        this.superAdminService = superAdminService;
        this.superAdminMapper = superAdminMapper;
    }

    @PostMapping("/admins")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un admin (SA-01)",
            description = "Crée l'entreprise, l'admin avec un mot de passe temporaire valide 24 h et un abonnement "
                    + "EN_ATTENTE_PAIEMENT : l'admin devra payer en ligne. Le mot de passe n'est jamais renvoyé ici ; "
                    + "avant l'étape 6 (courriels), il est écrit dans les logs en profil dev. 409 si le courriel existe.")
    public AdminDetailResponse creer(@Valid @RequestBody CreationAdminRequest request) {
        return superAdminMapper.toResponse(superAdminService.creerAdmin(superAdminMapper.toModel(request)));
    }

    @GetMapping("/tableau-de-bord")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Tableau de bord du super admin",
            description = "Nombre d'entreprises par statut d'abonnement affiché (recalculé à partir des dates), total "
                    + "des entreprises et demandes d'augmentation EN_ATTENTE.")
    public TableauDeBordSuperAdminResponse tableauDeBord() {
        return superAdminMapper.toResponse(superAdminService.tableauDeBord());
    }

    @GetMapping("/admins")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Lister les admins",
            description = "Paginé, du plus récent au plus ancien. Chaque ligne donne l'entreprise, l'admin, "
                    + "l'abonnement (formule, statut affiché, expiration, jours restants) et le nombre de gestionnaires. "
                    + "Filtres facultatifs et combinables.")
    public PageResponse<AdminLigneResponse> listerAdmins(
            @Parameter(description = "Statut d'abonnement affiché : ACTIF, EXPIRE_BIENTOT, EXPIRE, "
                    + "EN_ATTENTE_PAIEMENT, DESACTIVE")
            @RequestParam(required = false) StatutAbonnement statut,
            @Parameter(description = "Nom de l'entreprise, prénom, nom ou courriel de l'admin ; sans tenir compte des "
                    + "majuscules ni des accents, % et _ cherchés tels quels")
            @RequestParam(required = false) String recherche,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page doit être positif ou nul") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "taille doit être au moins 1")
            @Max(value = 100, message = "taille ne doit pas dépasser 100") int taille) {
        return superAdminMapper.toResponse(superAdminService.listerAdmins(statut, recherche, page, taille),
                superAdminMapper::toResponse);
    }

    @GetMapping("/admins/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Détail d'un admin (SA-03)",
            description = "L'admin, l'abonnement de son entreprise et son nombre de gestionnaires. "
                    + "404 si l'id ne désigne pas un admin, 403 pour le super admin.")
    public AdminDetailResponse detail(@PathVariable Long id) {
        return superAdminMapper.toResponse(superAdminService.detailAdmin(id));
    }

    @PutMapping("/admins/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Modifier un admin (SA-02)",
            description = "Prénom, nom, courriel et nom de l'entreprise. 409 si le courriel est déjà utilisé par un "
                    + "autre compte.")
    public AdminDetailResponse modifier(@PathVariable Long id, @Valid @RequestBody ModificationAdminRequest request) {
        return superAdminMapper.toResponse(superAdminService.modifierAdmin(id, superAdminMapper.toModel(request)));
    }

    @DeleteMapping("/admins/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer un admin (SA-02)",
            description = "Supprime aussi son entreprise, ses gestionnaires, son abonnement et ses paiements. "
                    + "Irréversible.")
    public void supprimer(@PathVariable Long id) {
        superAdminService.supprimerAdmin(id);
    }

    @PatchMapping("/admins/{id}/desactiver")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Désactiver un admin (SA-02)",
            description = "Bloque l'admin et ses gestionnaires ; l'abonnement passe à DESACTIVE.")
    public AdminDetailResponse desactiver(@PathVariable Long id) {
        return superAdminMapper.toResponse(superAdminService.changerActivation(id, false));
    }

    @PatchMapping("/admins/{id}/activer")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Activer un admin (SA-02)",
            description = "Réactive l'admin et ses gestionnaires ; le statut de l'abonnement est recalculé selon "
                    + "les dates (EN_ATTENTE_PAIEMENT, ACTIF, EXPIRE_BIENTOT ou EXPIRE).")
    public AdminDetailResponse activer(@PathVariable Long id) {
        return superAdminMapper.toResponse(superAdminService.changerActivation(id, true));
    }

    @PostMapping("/admins/{id}/mot-de-passe-temporaire")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Renvoyer un mot de passe temporaire",
            description = "Génère un nouveau mot de passe temporaire valide 24 h ; l'ancien ne fonctionne plus. "
                    + "Il n'est jamais renvoyé ici (logs en profil dev avant l'étape 6).")
    public MotDePasseTemporaireResponse renvoyerMotDePasse(@PathVariable Long id) {
        return superAdminMapper.toResponse(superAdminService.renvoyerMotDePasseTemporaire(id));
    }

    @GetMapping("/abonnements")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Lister les abonnements (SA-04, SA-06)",
            description = "Avec l'admin de chaque entreprise. Filtre facultatif par statut, recalculé à partir des "
                    + "dates. Ceux qui expirent le plus tôt en premier, les jamais payés à la fin.")
    public PageResponse<AbonnementAdminResponse> listerAbonnements(
            @Parameter(description = "Facultatif : ACTIF, EXPIRE_BIENTOT, EXPIRE, EN_ATTENTE_PAIEMENT, DESACTIVE")
            @RequestParam(required = false) StatutAbonnement statut,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page doit être positif ou nul") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "taille doit être au moins 1")
            @Max(value = 100, message = "taille ne doit pas dépasser 100") int taille) {
        return superAdminMapper.toResponse(superAdminService.listerAbonnements(statut, page, taille),
                superAdminMapper::toResponse);
    }
}
