package com.batulo.mappers;

import com.batulo.service.model.StatistiquesModel;
import com.batulo.service.model.StatistiquesResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mapper API : convertit uniquement Model -> Response.
 * Ne doit jamais connaître les entités JPA ni les requêtes SQL.
 */
@Component("apiStatistiqueMapper")
public class StatistiqueMapper {

    public StatistiquesResponse toResponse(StatistiquesModel m) {
        return new StatistiquesResponse(m.sondageId(), m.titre(), m.lignee(), m.versions(), m.du(), m.au(),
                new StatistiquesResponse.Resume(m.resume().nombreReponses(), m.resume().premiereReponse(),
                        m.resume().derniereReponse()),
                m.questions().stream().map(this::question).toList());
    }

    public StatistiquesResponse.PageTextes toResponse(StatistiquesModel.PageTextes p) {
        return new StatistiquesResponse.PageTextes(
                p.contenu().stream().map(t -> new StatistiquesResponse.Texte(t.texte(), t.dateSoumission())).toList(),
                p.page(), p.taille(), p.totalElements(), p.totalPages());
    }

    private StatistiquesResponse.QuestionStat question(StatistiquesModel.QuestionStat q) {
        return new StatistiquesResponse.QuestionStat(q.questionId(), q.cle(), q.section(), q.libelle(), q.type(),
                q.nombreRepondants(), q.sansReponse(),
                q.options() == null ? null : q.options().stream()
                        .map(o -> new StatistiquesResponse.OptionStat(o.valeur(), o.libelle(), o.nombre(), o.pourcentage()))
                        .toList(),
                q.numerique() == null ? null : new StatistiquesResponse.Numerique(q.numerique().moyenne(),
                        q.numerique().mediane(), q.numerique().min(), q.numerique().max(),
                        q.numerique().distribution().stream()
                                .map(c -> new StatistiquesResponse.Classe(c.valeur(), c.de(), c.a(), c.nombre(),
                                        c.pourcentage()))
                                .toList()),
                q.dates() == null ? null : new StatistiquesResponse.Dates(q.dates().min(), q.dates().max(),
                        q.dates().granularite(), q.dates().repartition().stream()
                        .map(p -> new StatistiquesResponse.Periode(p.periode(), p.nombre())).toList()),
                q.classement() == null ? null : q.classement().stream()
                        .map(r -> new StatistiquesResponse.RangStat(r.valeur(), r.libelle(), r.rangMoyen(), r.nombrePremier()))
                        .toList(),
                q.matrice() == null ? null : matrice(q.matrice()),
                q.textes() == null ? null : toResponse(q.textes()));
    }

    private StatistiquesResponse.Matrice matrice(StatistiquesModel.Matrice m) {
        List<StatistiquesResponse.LigneMatrice> lignes = m.lignes().stream()
                .map(l -> new StatistiquesResponse.LigneMatrice(l.ligne(), l.nombre(), l.cellules().stream()
                        .map(c -> new StatistiquesResponse.Cellule(c.colonne(), c.nombre(), c.pourcentage()))
                        .toList()))
                .toList();
        return new StatistiquesResponse.Matrice(m.colonnes(), lignes);
    }
}
