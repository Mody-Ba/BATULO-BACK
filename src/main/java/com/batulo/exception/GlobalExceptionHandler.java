package com.batulo.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Convertit toutes les erreurs en JSON { code, message }.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CompteExistantException.class)
    public ResponseEntity<ErrorResponse> handleCompteExistant(CompteExistantException ex) {
        return reponse(HttpStatus.CONFLICT, CompteExistantException.CODE, ex.getMessage());
    }

    @ExceptionHandler(RegleGestionException.class)
    public ResponseEntity<ErrorResponse> handleRegleGestion(RegleGestionException ex) {
        return reponse(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(ActionInterditeException.class)
    public ResponseEntity<ErrorResponse> handleActionInterdite(ActionInterditeException ex) {
        return reponse(HttpStatus.FORBIDDEN, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return reponse(HttpStatus.NOT_FOUND, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(StripeNonConfigureException.class)
    public ResponseEntity<ErrorResponse> handleStripeNonConfigure(StripeNonConfigureException ex) {
        return reponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(StripeIndisponibleException.class)
    public ResponseEntity<ErrorResponse> handleStripeIndisponible(StripeIndisponibleException ex) {
        return reponse(HttpStatus.BAD_GATEWAY, StripeIndisponibleException.CODE, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        // Seuls le champ et le message de la contrainte sont renvoyés, jamais la valeur rejetée
        // (qui pourrait être un mot de passe).
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " : " + fe.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining(" ; "));
        return reponse(HttpStatus.BAD_REQUEST, "VALIDATION", message);
    }

    /** Contraintes sur les paramètres d'URL (@Min, @Max sur page et taille...). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        String message = ex.getParameterValidationResults().stream()
                .flatMap(resultat -> resultat.getResolvableErrors().stream()
                        .map(erreur -> resultat.getMethodParameter().getParameterName() + " : " + erreur.getDefaultMessage()))
                .collect(Collectors.joining(" ; "));
        return reponse(HttpStatus.BAD_REQUEST, "VALIDATION", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        // Valeur d'énumération inconnue (ex. formule « HEBDO ») : on indique les valeurs acceptées.
        if (ex.getCause() instanceof InvalidFormatException invalide && invalide.getTargetType() != null
                && invalide.getTargetType().isEnum()) {
            String champ = invalide.getPath().isEmpty() ? "valeur" : invalide.getPath().getLast().getFieldName();
            String valeurs = Arrays.stream(invalide.getTargetType().getEnumConstants())
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
            return reponse(HttpStatus.BAD_REQUEST, "VALIDATION", champ + " : valeurs acceptées " + valeurs);
        }
        return reponse(HttpStatus.BAD_REQUEST, "REQUETE_INVALIDE", "Le corps de la requête est absent ou mal formé.");
    }

    /** Paramètre d'URL ou de chemin du mauvais type (id non numérique, statut inconnu...). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return reponse(HttpStatus.BAD_REQUEST, "PARAMETRE_INVALIDE", "Valeur invalide pour le paramètre " + ex.getName());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleParametreManquant(MissingServletRequestParameterException ex) {
        return reponse(HttpStatus.BAD_REQUEST, "PARAMETRE_MANQUANT", "Paramètre obligatoire manquant : " + ex.getParameterName());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return reponse(HttpStatus.METHOD_NOT_ALLOWED, "METHODE_NON_AUTORISEE",
                "La méthode " + ex.getMethod() + " n'est pas acceptée sur cette adresse.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return reponse(HttpStatus.NOT_FOUND, "INTROUVABLE", "Aucune ressource à l'adresse /" + ex.getResourcePath());
    }

    /** Contrainte de base violée (ex. deux inscriptions simultanées avec le même courriel). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrite(DataIntegrityViolationException ex) {
        String cause = String.valueOf(ex.getMostSpecificCause().getMessage());
        if (cause.contains("uk_utilisateur_courriel")) {
            return handleCompteExistant(new CompteExistantException());
        }
        log.warn("Violation d'intégrité : {}", cause);
        return reponse(HttpStatus.CONFLICT, "CONFLIT", "L'opération viole une contrainte de données.");
    }

    /** Filet de sécurité : le détail technique reste dans les logs, le client reçoit un message neutre. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleInattendue(Exception ex) {
        log.error("Erreur inattendue", ex);
        return reponse(HttpStatus.INTERNAL_SERVER_ERROR, "ERREUR_INTERNE", "Une erreur inattendue est survenue.");
    }

    private static ResponseEntity<ErrorResponse> reponse(HttpStatus statut, String code, String message) {
        return ResponseEntity.status(statut).contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(code, message));
    }
}
