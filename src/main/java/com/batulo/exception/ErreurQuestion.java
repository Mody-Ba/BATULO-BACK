package com.batulo.exception;

/** Erreur de validation sur la réponse à une question précise. */
public record ErreurQuestion(
        Long questionId,
        String message
) {
}
