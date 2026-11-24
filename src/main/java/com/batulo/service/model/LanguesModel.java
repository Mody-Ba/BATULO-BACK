package com.batulo.service.model;

import com.batulo.enums.Langue;

import java.util.List;

public record LanguesModel(Langue languePrincipale, List<Langue> languesSupplementaires) {
}
