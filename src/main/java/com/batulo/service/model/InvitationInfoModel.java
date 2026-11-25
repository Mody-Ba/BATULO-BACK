package com.batulo.service.model;

import com.batulo.enums.Langue;

/** Identité d'un invité (ajout, modification, import, reprise). */
public record InvitationInfoModel(String prenom, String nom, String courriel, String telephone, Langue langue) {
}
