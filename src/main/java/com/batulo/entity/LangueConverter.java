package com.batulo.entity;

import com.batulo.enums.Langue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Langue enregistrée par son code (« fr », « en », « ar »). */
@Converter
public class LangueConverter implements AttributeConverter<Langue, String> {

    @Override
    public String convertToDatabaseColumn(Langue langue) {
        return langue == null ? null : langue.getCode();
    }

    @Override
    public Langue convertToEntityAttribute(String code) {
        return code == null ? null : Langue.parCode(code)
                .orElseThrow(() -> new IllegalStateException("Langue inconnue en base : " + code));
    }
}
