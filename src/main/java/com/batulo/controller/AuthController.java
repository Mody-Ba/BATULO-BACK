package com.batulo.controller;

import com.batulo.configs.security.UtilisateurConnecte;
import com.batulo.mappers.AuthMapper;
import com.batulo.service.AuthService;
import com.batulo.service.model.ChangementMotDePasseRequest;
import com.batulo.service.model.ConnexionRequest;
import com.batulo.service.model.ConnexionResponse;
import com.batulo.service.model.JetonRafraichissementRequest;
import com.batulo.service.model.MessageResponse;
import com.batulo.service.model.MotDePasseOublieRequest;
import com.batulo.service.model.ProfilResponse;
import com.batulo.service.model.ReinitialisationMotDePasseRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Connexion, sessions et mots de passe. Routes publiques (déclarées dans WebSecurityConfig) : connexion,
 * rafraichir, mot-de-passe-oublie et reinitialiser-mot-de-passe. Les autres demandent un jeton ; elles restent
 * permises avec un mot de passe temporaire ou un abonnement en attente.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentification", description = "Connexion, jetons et mots de passe")
public class AuthController {

    private static final String MESSAGE_OUBLI = "Si un compte actif correspond à ce courriel, un lien de "
            + "réinitialisation valable 1 heure vient d'y être envoyé.";

    private final AuthService authService;
    private final AuthMapper authMapper;

    public AuthController(AuthService authService, AuthMapper authMapper) {
        this.authService = authService;
        this.authMapper = authMapper;
    }

    @PostMapping("/connexion")
    @SecurityRequirements
    @Operation(summary = "Se connecter (courriel + mot de passe)",
            description = "Renvoie le jeton d'accès (15 min) à coller dans « Authorize », le jeton de "
                    + "rafraîchissement, le rôle, le statut d'abonnement et motDePasseTemporaire. "
                    + "401 IDENTIFIANTS_INVALIDES ou MOT_DE_PASSE_TEMPORAIRE_EXPIRE, 403 COMPTE_DESACTIVE.")
    public ConnexionResponse connexion(@Valid @RequestBody ConnexionRequest request) {
        return authMapper.toResponse(authService.connexion(authMapper.toModel(request)));
    }

    @PostMapping("/rafraichir")
    @SecurityRequirements
    @Operation(summary = "Obtenir un nouveau jeton d'accès",
            description = "Échange le jeton de rafraîchissement contre une nouvelle paire de jetons. L'ancien est "
                    + "révoqué (usage unique) ; le réutiliser ferme toutes les sessions du compte.")
    public ConnexionResponse rafraichir(@Valid @RequestBody JetonRafraichissementRequest request) {
        return authMapper.toResponse(authService.rafraichir(request.jetonRafraichissement()));
    }

    @PostMapping("/deconnexion")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Se déconnecter", description = "Révoque le jeton de rafraîchissement de cette session.")
    public void deconnexion(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                            @Valid @RequestBody JetonRafraichissementRequest request) {
        authService.deconnexion(moi.id(), request.jetonRafraichissement());
    }

    @PostMapping("/mot-de-passe-oublie")
    @SecurityRequirements
    @Operation(summary = "Mot de passe oublié",
            description = "Envoie par courriel un lien valable 1 h et utilisable une seule fois. "
                    + "La réponse est la même que le courriel existe ou non.")
    public MessageResponse motDePasseOublie(@Valid @RequestBody MotDePasseOublieRequest request) {
        authService.motDePasseOublie(request.courriel());
        return new MessageResponse(MESSAGE_OUBLI);
    }

    @PostMapping("/reinitialiser-mot-de-passe")
    @SecurityRequirements
    @Operation(summary = "Choisir un nouveau mot de passe avec le lien reçu",
            description = "Le jeton est le paramètre « jeton » du lien. 400 JETON_INVALIDE s'il est inconnu, "
                    + "expiré ou déjà utilisé. Toutes les sessions du compte sont fermées.")
    public MessageResponse reinitialiserMotDePasse(@Valid @RequestBody ReinitialisationMotDePasseRequest request) {
        authService.reinitialiserMotDePasse(authMapper.toModel(request));
        return new MessageResponse("Mot de passe modifié. Vous pouvez vous connecter.");
    }

    @PostMapping("/changer-mot-de-passe")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Changer mon mot de passe",
            description = "Obligatoire après un mot de passe temporaire. Ferme les autres sessions et renvoie de "
                    + "nouveaux jetons (motDePasseTemporaire: false) : recollez le nouveau jeton dans « Authorize ».")
    public ConnexionResponse changerMotDePasse(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                               @Valid @RequestBody ChangementMotDePasseRequest request) {
        return authMapper.toResponse(authService.changerMotDePasse(moi.id(), authMapper.toModel(request)));
    }

    @GetMapping("/moi")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mon profil", description = "Le compte du jeton et le statut d'abonnement de son entreprise.")
    public ProfilResponse moi(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi) {
        return authMapper.toResponse(authService.moi(moi.id()));
    }
}
