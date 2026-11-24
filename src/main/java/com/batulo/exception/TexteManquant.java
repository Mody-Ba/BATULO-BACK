package com.batulo.exception;

import com.batulo.enums.Langue;

/** Texte non traduit : sa langue, sa clé (voir GET /api/sondages/{id}/traductions/{langue}) et le texte principal. */
public record TexteManquant(Langue langue, String cle, String source) {
}
