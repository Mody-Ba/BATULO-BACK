package com.batulo.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agrégations SQL des statistiques (module 4.3) : les réponses ne sont jamais chargées en mémoire. Chaque méthode
 * fait UNE requête pour toutes les questions concernées (désignées par leurs ids, toutes versions confondues) et
 * regroupe par clé de question (question.cle), ce qui cumule les versions d'une lignée.
 * Requêtes natives PostgreSQL (percentile_cont, width_bucket, fonctions JSONB).
 */
@Repository
public class StatistiqueRepository {

    private static final String DEPUIS_LIGNES = " from reponse_question rq join reponse r on r.id = rq.reponse_id "
            + "join question q on q.id = rq.question_id ";

    private final NamedParameterJdbcTemplate jdbc;

    public StatistiqueRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Réponses retenues : ces versions, et la période [debut, fin[ si elle est donnée. */
    public record Filtre(Collection<Long> versions, Instant debut, Instant fin) {
    }

    public record Resume(long nombre, Instant premiere, Instant derniere) {
    }

    public record Option(String cle, String valeur, long nombre) {
    }

    public record Booleen(String cle, boolean valeur, long nombre) {
    }

    public record Numerique(String cle, long nombre, BigDecimal moyenne, BigDecimal mediane, BigDecimal min,
                            BigDecimal max, long distinctes) {
    }

    public record Valeur(String cle, BigDecimal valeur, long nombre) {
    }

    /** Tranche 1 à 10 de [min, max] (width_bucket), la valeur max comprise dans la dernière. */
    public record Tranche(String cle, int tranche, long nombre) {
    }

    public record Jour(String cle, LocalDate jour, long nombre) {
    }

    public record Rang(String cle, String valeur, BigDecimal rangMoyen, long nombrePremier) {
    }

    public record Cellule(String cle, String ligne, String colonne, long nombre) {
    }

    public record Texte(String cle, String texte, Instant date) {
    }

    public Resume resume(Filtre filtre) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        return jdbc.queryForObject("select count(*), min(r.date_soumission), max(r.date_soumission) from reponse r where "
                + conditions(filtre, p), p, (rs, i) -> new Resume(rs.getLong(1), instant(rs, 2), instant(rs, 3)));
    }

    /** Nombre de réponses par version (pour « sansReponse »). */
    public Map<Long, Long> reponsesParVersion(Filtre filtre) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        Map<Long, Long> resultat = new HashMap<>();
        jdbc.query("select r.sondage_id, count(*) from reponse r where " + conditions(filtre, p)
                + " group by r.sondage_id", p, rs -> {
            resultat.put(rs.getLong(1), rs.getLong(2));
        });
        return resultat;
    }

    /** Nombre de réponses distinctes ayant au moins une valeur, par clé de question. */
    public Map<String, Long> repondants(Filtre filtre, Collection<Long> questions) {
        MapSqlParameterSource p = parametres(questions);
        Map<String, Long> resultat = new HashMap<>();
        jdbc.query("select q.cle, count(distinct rq.reponse_id)" + DEPUIS_LIGNES + "where " + conditions(filtre, p)
                + " and rq.question_id in (:questions) group by q.cle", p, rs -> {
            resultat.put(rs.getString(1), rs.getLong(2));
        });
        return resultat;
    }

    /** CHOIX_UNIQUE et CHOIX_MULTIPLE : réponses par valeur d'option (identique d'une version à l'autre). */
    public List<Option> options(Filtre filtre, Collection<Long> questions) {
        MapSqlParameterSource p = parametres(questions);
        return jdbc.query("select q.cle, o.valeur, count(distinct rq.reponse_id)" + DEPUIS_LIGNES
                + "join option_question o on o.id = rq.option_id where " + conditions(filtre, p)
                + " and rq.question_id in (:questions) group by q.cle, o.valeur", p,
                (rs, i) -> new Option(rs.getString(1), rs.getString(2), rs.getLong(3)));
    }

    public List<Booleen> booleens(Filtre filtre, Collection<Long> questions) {
        MapSqlParameterSource p = parametres(questions);
        return jdbc.query("select q.cle, rq.valeur_booleenne, count(*)" + DEPUIS_LIGNES + "where "
                + conditions(filtre, p) + " and rq.question_id in (:questions) and rq.valeur_booleenne is not null "
                + "group by q.cle, rq.valeur_booleenne", p,
                (rs, i) -> new Booleen(rs.getString(1), rs.getBoolean(2), rs.getLong(3)));
    }

    /** ECHELLE et NUMERIQUE : nombre, moyenne, médiane, min, max et nombre de valeurs distinctes. */
    public List<Numerique> numeriques(Filtre filtre, Collection<Long> questions) {
        MapSqlParameterSource p = parametres(questions);
        return jdbc.query("select q.cle, count(*), avg(rq.valeur_numerique), "
                + "percentile_cont(0.5) within group (order by rq.valeur_numerique), "
                + "min(rq.valeur_numerique), max(rq.valeur_numerique), count(distinct rq.valeur_numerique)"
                + DEPUIS_LIGNES + "where " + conditions(filtre, p)
                + " and rq.question_id in (:questions) and rq.valeur_numerique is not null group by q.cle", p,
                (rs, i) -> new Numerique(rs.getString(1), rs.getLong(2), rs.getBigDecimal(3), rs.getBigDecimal(4),
                        rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getLong(7)));
    }

    /** Distribution valeur par valeur (ECHELLE, NUMERIQUE avec peu de valeurs distinctes). */
    public List<Valeur> valeurs(Filtre filtre, Collection<Long> questions) {
        MapSqlParameterSource p = parametres(questions);
        return jdbc.query("select q.cle, rq.valeur_numerique, count(*)" + DEPUIS_LIGNES + "where "
                + conditions(filtre, p) + " and rq.question_id in (:questions) and rq.valeur_numerique is not null "
                + "group by q.cle, rq.valeur_numerique", p,
                (rs, i) -> new Valeur(rs.getString(1), rs.getBigDecimal(2), rs.getLong(3)));
    }

    /** Distribution en 10 tranches de même largeur entre le min et le max de chaque question. */
    public List<Tranche> tranches(Filtre filtre, Collection<Long> questions) {
        MapSqlParameterSource p = parametres(questions);
        return jdbc.query("select cle, least(width_bucket(v, mn, mx, 10), 10) as tranche, count(*) from ("
                + "select q.cle, rq.valeur_numerique v, min(rq.valeur_numerique) over (partition by q.cle) mn, "
                + "max(rq.valeur_numerique) over (partition by q.cle) mx" + DEPUIS_LIGNES + "where "
                + conditions(filtre, p) + " and rq.question_id in (:questions) and rq.valeur_numerique is not null"
                + ") t where mx > mn group by cle, tranche", p,
                (rs, i) -> new Tranche(rs.getString(1), rs.getInt(2), rs.getLong(3)));
    }

    /** DATE : nombre de réponses par jour (regroupé par mois ensuite si besoin). */
    public List<Jour> jours(Filtre filtre, Collection<Long> questions) {
        MapSqlParameterSource p = parametres(questions);
        return jdbc.query("select q.cle, rq.valeur_date, count(*)" + DEPUIS_LIGNES + "where " + conditions(filtre, p)
                + " and rq.question_id in (:questions) and rq.valeur_date is not null "
                + "group by q.cle, rq.valeur_date order by rq.valeur_date", p,
                (rs, i) -> new Jour(rs.getString(1), rs.getObject(2, LocalDate.class), rs.getLong(3)));
    }

    /** CLASSEMENT : rang moyen et nombre de fois classée première, par valeur d'option. */
    public List<Rang> rangs(Filtre filtre, Collection<Long> questions) {
        MapSqlParameterSource p = parametres(questions);
        return jdbc.query("select q.cle, e.valeur, avg(e.rang), count(*) filter (where e.rang = 1)" + DEPUIS_LIGNES
                + "cross join lateral jsonb_array_elements_text(rq.valeur_json) with ordinality as e(valeur, rang) "
                + "where " + conditions(filtre, p) + " and rq.question_id in (:questions) group by q.cle, e.valeur", p,
                (rs, i) -> new Rang(rs.getString(1), rs.getString(2), rs.getBigDecimal(3), rs.getLong(4)));
    }

    /** MATRICE : réponses par ligne et par colonne. */
    public List<Cellule> cellules(Filtre filtre, Collection<Long> questions) {
        MapSqlParameterSource p = parametres(questions);
        return jdbc.query("select q.cle, e.ligne, e.colonne, count(*)" + DEPUIS_LIGNES
                + "cross join lateral jsonb_each_text(rq.valeur_json) as e(ligne, colonne) "
                + "where " + conditions(filtre, p) + " and rq.question_id in (:questions) "
                + "group by q.cle, e.ligne, e.colonne", p,
                (rs, i) -> new Cellule(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4)));
    }

    /** Les « taille » textes les plus récents de chaque question. */
    public List<Texte> premiersTextes(Filtre filtre, Collection<Long> questions, int taille) {
        MapSqlParameterSource p = parametres(questions).addValue("taille", taille);
        return jdbc.query("select cle, texte, date from (select q.cle, rq.valeur_texte texte, r.date_soumission date, "
                + "row_number() over (partition by q.cle order by r.date_soumission desc, rq.id desc) n"
                + DEPUIS_LIGNES + "where " + conditions(filtre, p)
                + " and rq.question_id in (:questions) and rq.valeur_texte is not null) t where n <= :taille "
                + "order by cle, n", p, (rs, i) -> new Texte(rs.getString(1), rs.getString(2), instant(rs, 3)));
    }

    /** Une page de textes d'une question (toutes ses versions retenues), les plus récents d'abord. */
    public List<Texte> pageTextes(Filtre filtre, Collection<Long> questions, int page, int taille) {
        MapSqlParameterSource p = parametres(questions).addValue("taille", taille).addValue("debutPage", (long) page * taille);
        return jdbc.query("select q.cle, rq.valeur_texte, r.date_soumission" + DEPUIS_LIGNES + "where "
                + conditions(filtre, p) + " and rq.question_id in (:questions) and rq.valeur_texte is not null "
                + "order by r.date_soumission desc, rq.id desc limit :taille offset :debutPage", p,
                (rs, i) -> new Texte(rs.getString(1), rs.getString(2), instant(rs, 3)));
    }

    public long nombreTextes(Filtre filtre, Collection<Long> questions) {
        MapSqlParameterSource p = parametres(questions);
        Long nombre = jdbc.queryForObject("select count(*)" + DEPUIS_LIGNES + "where " + conditions(filtre, p)
                + " and rq.question_id in (:questions) and rq.valeur_texte is not null", p, Long.class);
        return nombre == null ? 0 : nombre;
    }

    private static MapSqlParameterSource parametres(Collection<Long> questions) {
        return new MapSqlParameterSource("questions", questions);
    }

    /** Conditions sur la réponse r, avec leurs paramètres (jamais de valeur concaténée dans le SQL). */
    private static String conditions(Filtre filtre, MapSqlParameterSource p) {
        StringBuilder sql = new StringBuilder("r.sondage_id in (:versions)");
        p.addValue("versions", filtre.versions());
        if (filtre.debut() != null) {
            sql.append(" and r.date_soumission >= :debut");
            p.addValue("debut", Timestamp.from(filtre.debut()));
        }
        if (filtre.fin() != null) {
            sql.append(" and r.date_soumission < :fin");
            p.addValue("fin", Timestamp.from(filtre.fin()));
        }
        return sql.toString();
    }

    private static Instant instant(ResultSet rs, int colonne) throws SQLException {
        Timestamp valeur = rs.getTimestamp(colonne);
        return valeur == null ? null : valeur.toInstant();
    }
}
