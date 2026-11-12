package com.batulo.repository;

import com.batulo.entity.Reponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface ReponseRepository extends JpaRepository<Reponse, Long> {

    /** Une seule réponse par appareil sur toute la lignée (sondages à réponse unique). */
    boolean existsByLigneeIdAndJetonRepondantHash(Long ligneeId, String jetonRepondantHash);

    long countBySondageId(Long sondageId);

    long countByLigneeId(Long ligneeId);

    @Query("select max(r.dateSoumission) from Reponse r where r.sondage.id = :sondageId")
    Instant derniereReponse(Long sondageId);

    /** Les valeurs sont chargées par lots (default_batch_fetch_size), sans pagination en mémoire. */
    Page<Reponse> findBySondageId(Long sondageId, Pageable pageable);

    /** Réponses de plusieurs versions en une requête : [sondageId, nombre] (absentes si aucune réponse). */
    @Query("select r.sondage.id, count(r) from Reponse r where r.sondage.id in :sondageIds group by r.sondage.id")
    List<Object[]> compterParSondage(Collection<Long> sondageIds);

    /** Réponses de plusieurs lignées (toutes versions) en une requête : [ligneeId, nombre]. */
    @Query("select r.lignee.id, count(r) from Reponse r where r.lignee.id in :ligneeIds group by r.lignee.id")
    List<Object[]> compterParLignee(Collection<Long> ligneeIds);

    /** Toutes les réponses reçues par les sondages d'une entreprise (tableau de bord de l'admin). */
    @Query("select count(r) from Reponse r where r.sondage.entreprise.id = :entrepriseId")
    long compterPourEntreprise(Long entrepriseId);
}
