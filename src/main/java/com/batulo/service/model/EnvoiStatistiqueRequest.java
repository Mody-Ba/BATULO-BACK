package com.batulo.service.model;

import com.batulo.enums.GranulariteDate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Envoi des statistiques d'un sondage terminé à son admin, avec les filtres à appliquer (comme GET /statistiques). */
public record EnvoiStatistiqueRequest(
        @Size(max = 2000, message = "le commentaire ne doit pas dépasser 2000 caractères")
        @Schema(example = "Collecte terminée : forte demande de semences à Kaolack.")
        String commentaire,

        @Schema(description = "true : toutes les versions du sondage", example = "false")
        Boolean lignee,

        LocalDate du,

        LocalDate au,

        @Schema(description = "Filtres de segmentation : cleDuCritere:valeur")
        @Size(max = 50, message = "trop de critères")
        List<String> critere,

        GranulariteDate granulariteDate
) {
}
