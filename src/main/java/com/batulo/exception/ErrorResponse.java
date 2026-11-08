package com.batulo.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Format unique des erreurs de l'API Batulo : un code stable (pour le frontend) et un message lisible.
 * « erreurs » détaille une erreur par question ; il n'apparaît que pour les réponses à un sondage.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        List<ErreurQuestion> erreurs
) {
    public ErrorResponse(String code, String message) {
        this(code, message, null);
    }
}
