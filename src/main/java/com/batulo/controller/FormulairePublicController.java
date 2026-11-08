package com.batulo.controller;

import com.batulo.mappers.CollecteMapper;
import com.batulo.service.CollecteService;
import com.batulo.service.model.ConfirmationReponseResponse;
import com.batulo.service.model.FormulairePublicResponse;
import com.batulo.service.model.SoumissionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Formulaire du répondant, PUBLIC (sans compte) : RE-01 à RE-03. Le jeton du lien désigne le sondage (toutes ses
 * versions) ; on répond à la version ouverte. Limite de requêtes par adresse IP (429 TROP_DE_REQUETES).
 */
@RestController
@RequestMapping("/api/public/sondages/{jeton}")
@SecurityRequirements
@Tag(name = "Public – répondre à un sondage", description = "Sans compte : lien ou QR code reçu par le répondant")
public class FormulairePublicController {

    private final CollecteService collecteService;
    private final CollecteMapper collecteMapper;

    public FormulairePublicController(CollecteService collecteService, CollecteMapper collecteMapper) {
        this.collecteService = collecteService;
        this.collecteMapper = collecteMapper;
    }

    @GetMapping
    @Operation(summary = "Formulaire à remplir",
            description = "Titre, description, sections, questions, options et configuration, sans donnée interne. "
                    + "404 SONDAGE_INTROUVABLE, 403 SONDAGE_NON_OUVERT (ouverture planifiée), 410 SONDAGE_FERME.")
    public FormulairePublicResponse formulaire(@PathVariable String jeton) {
        return collecteMapper.toResponse(collecteService.formulaire(jeton));
    }

    @PostMapping("/reponses")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Envoyer ses réponses",
            description = "Options désignées par leur valeur, matrice par {\"ligne\": \"colonne\"}. 400 REPONSES_INVALIDES "
                    + "(une erreur par question) ou JETON_REPONDANT_INVALIDE, 409 DEJA_REPONDU (réponse unique par "
                    + "appareil), 403/410 si le sondage n'est pas ouvert.")
    public ConfirmationReponseResponse repondre(@PathVariable String jeton,
                                                @Valid @RequestBody SoumissionRequest request) {
        return collecteMapper.toConfirmation(collecteService.soumettre(jeton, collecteMapper.toModel(request)));
    }
}
