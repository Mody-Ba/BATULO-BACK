package com.batulo.repository;

import com.batulo.enums.TypeQuestion;
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
 * Agrégations SQL des statistiques (module 4.3), de la segmentation et des tableaux croisés : les réponses ne sont jamais chargées en mémoire. Chaque méthode
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

    /**
     * Réponses retenues : ces versions, la période [debut, fin[ si elle est donnée, et les critères (segmentation) :
     * plusieurs valeurs d'un même critère se combinent en OU, des critères différents en ET.
     */
    public record Filtre(Collection<Long> versions, Instant debut, Instant fin, List<FiltreCritere> criteres) {
        public Filtre(Collection<Long> versions, Instant debut, Instant fin) {
            this(versions, debut, fin, List.of());
        }
    }

    /**
     * Valeur d'un critère pour une réponse : code d'option (CHOIX_UNIQUE), 'true'/'false' (OUI_NON), code de tranche
     * t1..tN (NUMERIQUE, bornes [a ; b[), 'AAAA-MM' ou 'AAAA' (DATE).
     */
    public record Decoupage(TypeQuestion type, List<BigDecimal> bornes, String granularite) {
    }

    /** Le répondant a donné au critère (questions de même clé) l'une de ces valeurs. */
    public record FiltreCritere(Collection<Long> questions, Decoupage decoupage, Collection<String> valeurs) {
    }

    /** Nombre de réponses par valeur du critère (colonne, null : non renseigné) et par modalité de la question. */
    public record Croisement(String colonne, String modalite, long nombre) {
    }

    public record NumeriqueParColonne(String colonne, long nombre, BigDecimal moyenne, BigDecimal mediane,
                                      BigDecimal min, BigDecimal max) {
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

    /** Valeurs d'un critère réellement présentes dans les réponses (critère DATE : mois ou années). */
    public List<String> valeursCritere(Filtre filtre, Collection<Long> questions, Decoupage decoupage) {
        MapSqlParameterSource p = parametres(questions);
        String valeur = expressionCritere("rq", "o", decoupage, "dv", p);
        return jdbc.queryForList("select distinct " + valeur + DEPUIS_LIGNES
                + "left join option_question o on o.id = rq.option_id where " + conditions(filtre, p)
                + " and rq.question_id in (:questions) order by 1", p, String.class);
    }

    /**
     * Tableau croisé : pour chaque réponse à la question, sa modalité et la valeur du critère donnée dans la MÊME
     * réponse (null si le critère n'a pas été renseigné).
     */
    public List<Croisement> croisement(Filtre filtre, Collection<Long> questions, TypeQuestion type,
                                       Collection<Long> questionsCritere, Decoupage critere) {
        MapSqlParameterSource p = parametres(questions);
        String modalite = switch (type) {
            case CHOIX_UNIQUE, CHOIX_MULTIPLE -> "o.valeur";
            case OUI_NON -> "case when rq.valeur_booleenne then 'true' else 'false' end";
            default -> "cast(rq.valeur_numerique as varchar)";
        };
        return jdbc.query(avecCritere(questionsCritere, critere, p) + "select c.col, " + modalite
                + ", count(distinct rq.reponse_id)" + DEPUIS_LIGNES + "left join option_question o on o.id = rq.option_id "
                + "left join critere c on c.reponse_id = rq.reponse_id where " + conditions(filtre, p)
                + " and rq.question_id in (:questions) group by c.col, " + modalite, p,
                (rs, i) -> new Croisement(rs.getString(1), rs.getString(2), rs.getLong(3)));
    }

    /** Répondants à la question, par valeur du critère (base des pourcentages de colonne). */
    public Map<String, Long> repondantsParColonne(Filtre filtre, Collection<Long> questions,
                                                 Collection<Long> questionsCritere, Decoupage critere) {
        MapSqlParameterSource p = parametres(questions);
        Map<String, Long> resultat = new HashMap<>();
        jdbc.query(avecCritere(questionsCritere, critere, p) + "select c.col, count(distinct rq.reponse_id)"
                + DEPUIS_LIGNES + "left join critere c on c.reponse_id = rq.reponse_id where " + conditions(filtre, p)
                + " and rq.question_id in (:questions) group by c.col", p, rs -> {
            resultat.put(rs.getString(1), rs.getLong(2));
        });
        return resultat;
    }

    /** ECHELLE, NUMERIQUE : nombre, moyenne, médiane, min et max par valeur du critère (ex. poids moyen par région). */
    public List<NumeriqueParColonne> numeriqueParColonne(Filtre filtre, Collection<Long> questions,
                                                         Collection<Long> questionsCritere, Decoupage critere) {
        MapSqlParameterSource p = parametres(questions);
        return jdbc.query(avecCritere(questionsCritere, critere, p) + "select c.col, count(*), avg(rq.valeur_numerique), "
                + "percentile_cont(0.5) within group (order by rq.valeur_numerique), min(rq.valeur_numerique), "
                + "max(rq.valeur_numerique)" + DEPUIS_LIGNES + "left join critere c on c.reponse_id = rq.reponse_id "
                + "where " + conditions(filtre, p) + " and rq.question_id in (:questions) "
                + "and rq.valeur_numerique is not null group by c.col", p,
                (rs, i) -> new NumeriqueParColonne(rs.getString(1), rs.getLong(2), rs.getBigDecimal(3),
                        rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6)));
    }

    /** CTE « critere » : (reponse_id, col) = valeur du critère de chaque réponse. */
    private static String avecCritere(Collection<Long> questionsCritere, Decoupage critere, MapSqlParameterSource p) {
        p.addValue("questionsCritere", questionsCritere);
        return "with critere as (select rc.reponse_id, " + expressionCritere("rc", "oc", critere, "cc", p)
                + " as col from reponse_question rc left join option_question oc on oc.id = rc.option_id "
                + "where rc.question_id in (:questionsCritere)) ";
    }

    /** Expression SQL de la valeur d'un critère, sur la ligne « ligne » (et son option « option »). */
    private static String expressionCritere(String ligne, String option, Decoupage decoupage, String prefixe,
                                            MapSqlParameterSource p) {
        return switch (decoupage.type()) {
            case CHOIX_UNIQUE -> option + ".valeur";
            case OUI_NON -> "case when " + ligne + ".valeur_booleenne then 'true' else 'false' end";
            case NUMERIQUE -> {
                StringBuilder sql = new StringBuilder("case");
                List<BigDecimal> bornes = decoupage.bornes();
                for (int i = 0; i < bornes.size(); i++) {
                    String parametre = prefixe + "b" + i;
                    p.addValue(parametre, bornes.get(i));
                    sql.append(" when ").append(ligne).append(".valeur_numerique < :").append(parametre)
                            .append(" then 't").append(i + 1).append("'");
                }
                yield sql.append(" else 't").append(bornes.size() + 1).append("' end").toString();
            }
            case DATE -> "to_char(" + ligne + ".valeur_date, '" + ("ANNEE".equals(decoupage.granularite())
                    ? "YYYY" : "YYYY-MM") + "')";
            default -> throw new IllegalArgumentException("Type non éligible comme critère : " + decoupage.type());
        };
    }

    private static MapSqlParameterSource parametres(Collection<Long> questions) {
        return new MapSqlParameterSource("questions", questions);
    }

    /**
     * Conditions sur la réponse r (versions, période, critères), avec leurs paramètres (jamais de valeur concaténée
     * dans le SQL). Partagées avec ExportRepository.
     */
    static String conditions(Filtre filtre, MapSqlParameterSource p) {
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
        for (int i = 0; i < filtre.criteres().size(); i++) {
            FiltreCritere critere = filtre.criteres().get(i);
            String q = "fq" + i;
            String v = "fv" + i;
            p.addValue(q, critere.questions());
            p.addValue(v, critere.valeurs());
            sql.append(" and exists (select 1 from reponse_question rc").append(i)
                    .append(" left join option_question oc").append(i).append(" on oc").append(i).append(".id = rc")
                    .append(i).append(".option_id where rc").append(i).append(".reponse_id = r.id and rc").append(i)
                    .append(".question_id in (:").append(q).append(") and ")
                    .append(expressionCritere("rc" + i, "oc" + i, critere.decoupage(), "fb" + i + "_", p))
                    .append(" in (:").append(v).append("))");
        }
        return sql.toString();
    }

    private static Instant instant(ResultSet rs, int colonne) throws SQLException {
        Timestamp valeur = rs.getTimestamp(colonne);
        return valeur == null ? null : valeur.toInstant();
    }
}
