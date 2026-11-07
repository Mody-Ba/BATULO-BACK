package com.batulo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Dates lisibles pour les notifications (« 7 décembre 2026 »), dans le fuseau de batulo.fuseau-horaire. */
@Component
public class FormatDate {

    private final DateTimeFormatter jour;

    public FormatDate(@Value("${batulo.fuseau-horaire}") String fuseauHoraire) {
        this.jour = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.CANADA_FRENCH).withZone(ZoneId.of(fuseauHoraire));
    }

    public String jour(Instant instant) {
        return jour.format(instant);
    }
}
