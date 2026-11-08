package com.batulo.mappers;

import com.batulo.exception.RegleGestionException;
import com.batulo.service.model.CritereModel;
import com.batulo.service.model.CritereResponse;
import com.batulo.service.model.CroisementModel;
import com.batulo.service.model.CroisementResponse;
import com.batulo.service.model.StatistiquesModel;
import com.batulo.service.model.StatistiquesResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapper API : convertit uniquement Model -> Response.
 * Ne doit jamais connaître les entités JPA ni les requêtes SQL.
 */
@Component("apiStatistiqueMapper")
public class StatistiqueMapper {

    public StatistiquesResponse toResponse(StatistiquesModel m) {
        return new StatistiquesResponse(m.sondageId(), m.titre(), m.lignee(), m.versions(), m.du(), m.au(),
                toCritereResponses(m.criteres()),
                new StatistiquesResponse.Resume(m.resume().nombreReponses(), m.resume().petitEffectif(),
                        m.resume().premiereReponse(), m.resume().derniereReponse()),
                m.questions().stream().map(this::question).toList());
    }

    public StatistiquesResponse.PageTextes toResponse(StatistiquesModel.PageTextes p) {
        return new StatistiquesResponse.PageTextes(
                p.contenu().stream().map(t -> new StatistiquesResponse.Texte(t.texte(), t.dateSoumission())).toList(),
                p.page(), p.taille(), p.totalElements(), p.totalPages());
    }

    private StatistiquesResponse.QuestionStat question(StatistiquesModel.QuestionStat q) {
        return new StatistiquesResponse.QuestionStat(q.questionId(), q.cle(), q.section(), q.libelle(), q.type(),
                q.nombreRepondants(), q.sansReponse(), q.petitEffectif(),
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

    /** « cle:valeur » (filtres répétables) -> clé de critère -> valeurs. La clé est un UUID, sans « : ». */
    public Map<String, List<String>> toCriteres(List<String> filtres) {
        Map<String, List<String>> criteres = new LinkedHashMap<>();
        if (filtres == null) {
            return criteres;
        }
        for (String filtre : filtres) {
            int separateur = filtre.indexOf(':');
            if (separateur <= 0 || separateur == filtre.length() - 1) {
                throw new RegleGestionException("CRITERE_INVALIDE",
                        "Filtre « " + filtre + " » : écrivez critere=cleDuCritere:valeur.");
            }
            criteres.computeIfAbsent(filtre.substring(0, separateur).trim(), c -> new ArrayList<>())
                    .add(filtre.substring(separateur + 1).trim());
        }
        return criteres;
    }

    public List<CritereResponse> toCritereResponses(List<CritereModel> criteres) {
        return criteres.stream()
                .map(c -> new CritereResponse(c.cle(), c.questionId(), c.libelle(), c.type(), c.valeurs().stream()
                        .map(v -> new CritereResponse.ValeurCritere(v.valeur(), v.libelle())).toList()))
                .toList();
    }

    public CroisementResponse toResponse(CroisementModel m) {
        return new CroisementResponse(
                new CroisementResponse.Entete(m.question().cle(), m.question().libelle(), m.question().type()),
                new CroisementResponse.Entete(m.par().cle(), m.par().libelle(), m.par().type()),
                toCritereResponses(m.criteres()),
                m.colonnes().stream().map(c -> new CroisementResponse.Colonne(c.valeur(), c.libelle(),
                        c.nombreRepondants(), c.petitEffectif())).toList(),
                m.lignes() == null ? null : m.lignes().stream().map(l -> new CroisementResponse.Ligne(l.valeur(),
                        l.libelle(), l.total(), l.cellules().stream().map(c -> new CroisementResponse.Cellule(
                        c.colonne(), c.nombre(), c.pourcentageLigne(), c.pourcentageColonne(), c.petitEffectif()))
                        .toList())).toList(),
                m.numerique() == null ? null : m.numerique().stream().map(n -> new CroisementResponse.NumeriqueColonne(
                        n.colonne(), n.nombre(), n.moyenne(), n.mediane(), n.min(), n.max(), n.petitEffectif())).toList());
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
