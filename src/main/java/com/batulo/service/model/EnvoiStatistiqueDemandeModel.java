package com.batulo.service.model;

import com.batulo.enums.GranulariteDate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record EnvoiStatistiqueDemandeModel(
        String commentaire,
        boolean lignee,
        LocalDate du,
        LocalDate au,
        Map<String, List<String>> criteres,
        GranulariteDate granulariteDate
) {
}
