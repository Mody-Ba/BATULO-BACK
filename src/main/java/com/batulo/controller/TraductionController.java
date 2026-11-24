package com.batulo.controller;

import com.batulo.configs.security.UtilisateurConnecte;
import com.batulo.mappers.SondageMapper;
import com.batulo.mappers.TraductionMapper;
import com.batulo.service.Langues;
import com.batulo.service.TraductionService;
import com.batulo.service.model.CompletudeLangueResponse;
import com.batulo.service.model.LanguesRequest;
import com.batulo.service.model.SondageResponse;
import com.batulo.service.model.TraductionsLangueResponse;
import com.batulo.service.model.TraductionsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Langues et traductions du contenu d'un sondage (fr, en, ar). Le contenu se saisit dans la langue principale ; les
 * langues supplémentaires se traduisent ici, texte par texte. Écriture : brouillon seulement (409 SONDAGE_PUBLIE),
 * mêmes droits que la modification du sondage. La publication exige des langues supplémentaires complètes.
 */
@RestController
@RequestMapping("/api/sondages/{id}")
@Tag(name = "Sondages – langues et traductions", description = "Langue principale, langues supplémentaires, "
        + "traductions côte à côte et complétude")
public class TraductionController {

    private static final String ROLES = "hasAnyRole('ADMIN', 'GESTIONNAIRE')";

    private final TraductionService traductionService;
    private final TraductionMapper traductionMapper;
    private final SondageMapper sondageMapper;

    public TraductionController(TraductionService traductionService, TraductionMapper traductionMapper,
                                SondageMapper sondageMapper) {
        this.traductionService = traductionService;
        this.traductionMapper = traductionMapper;
        this.sondageMapper = sondageMapper;
    }

    @PutMapping("/langues")
    @PreAuthorize(ROLES)
    @Operation(summary = "Définir les langues du sondage",
            description = "Brouillon seulement. Langue principale (celle de la saisie) et langues supplémentaires (sans "
                    + "la principale, sans doublon : 400 LANGUE_INVALIDE). Changer la langue principale ne change que "
                    + "l'étiquette (rien n'est traduit ni échangé) ; les traductions d'une langue retirée sont gardées "
                    + "mais ignorées. Renvoie le sondage.")
    public SondageResponse langues(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                   @PathVariable Long id, @Valid @RequestBody LanguesRequest request) {
        return sondageMapper.toResponse(traductionService.definirLangues(moi.id(), id, traductionMapper.toModel(request)));
    }

    @GetMapping("/traductions")
    @PreAuthorize(ROLES)
    @Operation(summary = "Complétude des traductions",
            description = "Pour chaque langue supplémentaire : textes traduits / total et liste des textes manquants "
                    + "(clé et texte principal). Seuls les textes non vides de la langue principale comptent.")
    public List<CompletudeLangueResponse> completude(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id) {
        return traductionMapper.toResponses(traductionService.completude(moi.id(), id));
    }

    @GetMapping("/traductions/{langue}")
    @PreAuthorize(ROLES)
    @Operation(summary = "Textes d'une langue, côte à côte",
            description = "Chaque texte traduisible dans l'ordre du formulaire : clé, élément, texte principal (source) "
                    + "et traduction (null si absente). Clés : sondage.titre, sondage.description, sondage.objetEnquete, "
                    + "sondage.texteConfidentialite, sondage.texteConsentement, section.{id}.titre|description, "
                    + "question.{id}.libelle|aide|libelleMin|libelleMax, question.{id}.ligne.{n}, "
                    + "question.{id}.colonne.{n}, option.{id}.libelle. 400 LANGUE_INVALIDE si ce n'est pas une langue "
                    + "supplémentaire du sondage.")
    public TraductionsLangueResponse traductions(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id,
            @Parameter(description = "en, ar ou fr") @PathVariable String langue) {
        return traductionMapper.toResponse(traductionService.traductions(moi.id(), id, Langues.lire(langue)));
    }

    @PutMapping("/traductions/{langue}")
    @PreAuthorize(ROLES)
    @Operation(summary = "Enregistrer les traductions d'une langue",
            description = "En une fois, brouillon seulement : chaque texte envoyé est enregistré (null ou vide : effacé), "
                    + "un texte absent reste inchangé. 400 TRADUCTION_INVALIDE (clé inconnue ou en double, traduction "
                    + "plus longue que le texte principal ne peut l'être), LANGUE_INVALIDE ; 409 SONDAGE_PUBLIE. "
                    + "Un texte principal modifié ensuite perd ses traductions.")
    public TraductionsLangueResponse enregistrer(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id,
            @PathVariable String langue, @Valid @RequestBody TraductionsRequest request) {
        return traductionMapper.toResponse(traductionService.enregistrer(moi.id(), id, Langues.lire(langue),
                traductionMapper.toTextes(request)));
    }
}
