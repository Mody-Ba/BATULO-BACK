package com.batulo.repository;

import com.batulo.repository.StatistiqueRepository.Filtre;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lecture EN FLUX des réponses brutes pour les exports : une seule requête triée par réponse, lue par paquets de
 * 1000 lignes (curseur PostgreSQL, qui exige une transaction). La mémoire ne dépend pas du nombre de réponses.
 */
@Repository
public class ExportRepository {

    static final int TAILLE_PAQUET = 1000;

    private final NamedParameterJdbcTemplate jdbc;

    public ExportRepository(DataSource dataSource) {
        JdbcTemplate flux = new JdbcTemplate(dataSource);
        flux.setFetchSize(TAILLE_PAQUET);
        this.jdbc = new NamedParameterJdbcTemplate(flux);
    }

    /**
     * Une ligne SQL : une valeur d'une réponse (ou une réponse sans valeur retenue : cle null).
     * Les colonnes valeur_* suivent le rangement de reponse_question.
     */
    public record LigneBrute(long reponseId, Instant dateSoumission, int version, String cle, String valeurOption,
                             BigDecimal valeurNumerique, String valeurTexte, LocalDate valeurDate,
                             Boolean valeurBooleenne, String valeurJson) {
    }

    /** Parcourt les valeurs des réponses retenues, triées par réponse (toutes les lignes d'une réponse se suivent). */
    public void parcourir(Filtre filtre, Collection<Long> questions, Consumer<LigneBrute> traitement) {
        MapSqlParameterSource p = new MapSqlParameterSource("questions", questions.isEmpty() ? List.of(-1L) : questions);
        jdbc.query("select r.id, r.date_soumission, so.version, q.cle, o.valeur, rq.valeur_numerique, rq.valeur_texte, "
                + "rq.valeur_date, rq.valeur_booleenne, cast(rq.valeur_json as varchar) from reponse r "
                + "join sondage so on so.id = r.sondage_id "
                + "left join reponse_question rq on rq.reponse_id = r.id and rq.question_id in (:questions) "
                + "left join question q on q.id = rq.question_id "
                + "left join option_question o on o.id = rq.option_id "
                + "where " + StatistiqueRepository.conditions(filtre, p) + " order by r.id, rq.id", p, rs -> {
            Timestamp date = rs.getTimestamp(2);
            Object booleen = rs.getObject(9);
            traitement.accept(new LigneBrute(rs.getLong(1), date.toInstant(), rs.getInt(3), rs.getString(4),
                    rs.getString(5), rs.getBigDecimal(6), rs.getString(7), rs.getObject(8, LocalDate.class),
                    booleen == null ? null : (Boolean) booleen, rs.getString(10)));
        });
    }
}
