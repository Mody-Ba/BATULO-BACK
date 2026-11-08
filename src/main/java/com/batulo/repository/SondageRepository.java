package com.batulo.repository;

import com.batulo.entity.Sondage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SondageRepository extends JpaRepository<Sondage, Long> {

    /** Toujours filtré par entreprise : un sondage d'une autre entreprise est introuvable (404). */
    @EntityGraph(attributePaths = "creePar")
    Optional<Sondage> findByIdAndEntrepriseId(Long id, Long entrepriseId);

    @EntityGraph(attributePaths = "creePar")
    Page<Sondage> findByEntrepriseId(Long entrepriseId, Pageable pageable);

    @EntityGraph(attributePaths = "creePar")
    Page<Sondage> findByEntrepriseIdAndCreeParId(Long entrepriseId, Long creeParId, Pageable pageable);

    /** Nombre de sections et de questions de plusieurs sondages en une requête : [sondageId, sections, questions]. */
    @Query("select s.id, count(distinct se.id), count(q.id) from Sondage s "
            + "left join s.sections se left join se.questions q where s.id in :ids group by s.id")
    List<Object[]> compterContenu(Collection<Long> ids);
}
