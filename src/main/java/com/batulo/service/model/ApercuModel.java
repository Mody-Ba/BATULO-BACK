package com.batulo.service.model;

import com.batulo.enums.StatutSondage;

import java.util.List;

/**
 * Prévisualisation : le formulaire tel que le verra le répondant, sans les informations internes
 * (créateur, entreprise, dates de création et de modification).
 */
public record ApercuModel(
        Long id,
        String titre,
        String description,
        int version,
        StatutSondage statut,
        List<SectionModel> sections
) {
}
