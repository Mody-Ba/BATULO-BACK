package com.batulo.service.model;

import com.batulo.enums.Langue;

import java.util.List;

public record CompletudeLangueModel(Langue langue, int traduits, int total, boolean complet, List<Manquant> manquants) {

    public record Manquant(String cle, String source) {
    }
}
