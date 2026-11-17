package com.batulo.exception;

/** Fichier envoyé au-delà de la taille permise (413). */
public class FichierTropGrosException extends RuntimeException {

    public FichierTropGrosException(String message) {
        super(message);
    }
}
