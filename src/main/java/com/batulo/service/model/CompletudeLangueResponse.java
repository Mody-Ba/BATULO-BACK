package com.batulo.service.model;

import com.batulo.enums.Langue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** État d'une langue supplémentaire : textes traduits / total, et textes encore à traduire. */
public record CompletudeLangueResponse(Langue langue, int traduits, int total, boolean complet,
                                       List<Manquant> manquants) {

    @Schema(name = "TexteNonTraduit")
    public record Manquant(String cle, String source) {
    }
}
