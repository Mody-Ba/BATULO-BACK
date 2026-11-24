package com.batulo.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;
import java.util.Optional;

/**
 * Langue du contenu d'un sondage (langue principale, traductions) et langue choisie par le répondant.
 * Écrite en JSON et en base par son code ISO 639-1 en minuscules : « fr », « en », « ar ».
 */
public enum Langue {
    FR("fr"),
    EN("en"),
    AR("ar");

    private final String code;

    Langue(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    /** « fr », « EN », « Ar »... ; vide si le code n'est pas une langue proposée. */
    public static Optional<Langue> parCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String normalise = code.trim().toLowerCase(Locale.ROOT);
        for (Langue langue : values()) {
            if (langue.code.equals(normalise)) {
                return Optional.of(langue);
            }
        }
        return Optional.empty();
    }

    /** Lecture JSON : code inconnu = null (le service répond alors 400 LANGUE_INVALIDE s'il la contrôle). */
    @JsonCreator
    public static Langue depuisJson(String code) {
        return parCode(code).orElse(null);
    }

    @Override
    public String toString() {
        return code;
    }
}
