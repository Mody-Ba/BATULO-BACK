package com.batulo.controller;

import com.batulo.mappers.InscriptionMapper;
import com.batulo.service.InscriptionService;
import com.batulo.service.model.InscriptionRequest;
import com.batulo.service.model.InscriptionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inscription publique de l'admin d'une entreprise (l'appelant n'a pas encore de compte).
 */
@RestController
@RequestMapping("/api/auth")
@SecurityRequirements
@Tag(name = "Inscription", description = "Inscription de l'admin d'une entreprise")
public class InscriptionController {

    private final InscriptionService inscriptionService;
    private final InscriptionMapper inscriptionMapper;

    public InscriptionController(InscriptionService inscriptionService, InscriptionMapper inscriptionMapper) {
        this.inscriptionService = inscriptionService;
        this.inscriptionMapper = inscriptionMapper;
    }

    @PostMapping("/inscription")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "S'inscrire (admin d'entreprise)",
            description = "Crée l'entreprise, le compte admin et un abonnement « En attente de paiement ». "
                    + "Connectez-vous ensuite : seuls /api/auth/** et /api/abonnements/** sont permis tant que le paiement n'est pas validé. "
                    + "400 si un champ est invalide ou si la confirmation ne correspond pas, "
                    + "409 si un compte existe déjà avec ce courriel.")
    public InscriptionResponse inscription(@Valid @RequestBody InscriptionRequest request) {
        return inscriptionMapper.toResponse(inscriptionService.inscrire(inscriptionMapper.toModel(request)));
    }
}
