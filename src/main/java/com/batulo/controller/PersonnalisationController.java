package com.batulo.controller;

import com.batulo.configs.security.UtilisateurConnecte;
import com.batulo.mappers.PersonnalisationMapper;
import com.batulo.service.PersonnalisationService;
import com.batulo.service.model.PersonnalisationRequest;
import com.batulo.service.model.PersonnalisationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@Tag(name = "Personnalisation", description = "Logo, couleur et nom affiché des formulaires de l'entreprise")
public class PersonnalisationController {

    private final PersonnalisationService personnalisationService;
    private final PersonnalisationMapper personnalisationMapper;

    public PersonnalisationController(PersonnalisationService personnalisationService,
                                      PersonnalisationMapper personnalisationMapper) {
        this.personnalisationService = personnalisationService;
        this.personnalisationMapper = personnalisationMapper;
    }

    @GetMapping("/api/entreprise/personnalisation")
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTIONNAIRE')")
    @Operation(summary = "Personnalisation de mon entreprise",
            description = "Lisible par l'admin et ses gestionnaires. Valeurs saisies (null si absentes) et effectives "
                    + "(couleur Batulo et nom de l'entreprise par défaut), couleur de texte lisible calculée.")
    public PersonnalisationResponse lire(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi) {
        return personnalisationMapper.toResponse(personnalisationService.lire(moi.id()));
    }

    @PutMapping("/api/admin/personnalisation")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Modifier le nom affiché et la couleur",
            description = "Admin seulement. Champ absent : inchangé ; \"\" : effacé. 400 COULEUR_INVALIDE si la "
                    + "couleur n'est pas au format #RRGGBB.")
    public PersonnalisationResponse modifier(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                             @Valid @RequestBody PersonnalisationRequest request) {
        return personnalisationMapper.toResponse(
                personnalisationService.modifier(moi.id(), personnalisationMapper.toModel(request)));
    }

    @PutMapping(value = "/api/admin/personnalisation/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Ajouter ou remplacer le logo",
            description = "Admin seulement. multipart/form-data, champ « fichier ». PNG ou JPEG uniquement, reconnus "
                    + "par les premiers octets du fichier (extension et Content-Type ignorés) : 400 LOGO_FORMAT_INVALIDE "
                    + "(SVG, GIF, WebP, faux PNG...). 1 Mo au plus (413 FICHIER_TROP_GROS), 2000 x 2000 pixels au plus "
                    + "(400 LOGO_DIMENSIONS), image lisible (400 LOGO_ILLISIBLE). L'URL du logo change à chaque "
                    + "remplacement.")
    public PersonnalisationResponse remplacerLogo(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @RequestPart("fichier") MultipartFile fichier) throws IOException {
        return personnalisationMapper.toResponse(personnalisationService.remplacerLogo(moi.id(), fichier.getBytes()));
    }

    @DeleteMapping("/api/admin/personnalisation/logo")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Supprimer le logo", description = "Admin seulement. Son URL ne répond plus (404).")
    public PersonnalisationResponse supprimerLogo(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi) {
        return personnalisationMapper.toResponse(personnalisationService.supprimerLogo(moi.id()));
    }
}
