package com.batulo.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Format unique des erreurs de l'API Batulo : un code stable (pour le frontend) et un message lisible.
 * « erreurs » détaille une erreur par question ; il n'apparaît que pour les réponses à un sondage.
 * « manquants » liste les textes non traduits ; il n'apparaît que pour TRADUCTIONS_INCOMPLETES.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        List<ErreurQuestion> erreurs,
        List<TexteManquant> manquants
) {
    public ErrorResponse(String code, String message) {
        this(code, message, null, null);
    }

    public ErrorResponse(String code, String message, List<ErreurQuestion> erreurs) {
        this(code, message, erreurs, null);
    }
}
