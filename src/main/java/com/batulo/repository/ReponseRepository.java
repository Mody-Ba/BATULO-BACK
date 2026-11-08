package com.batulo.repository;

import com.batulo.entity.Reponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;

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
}
