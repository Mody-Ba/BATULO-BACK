package com.batulo.repository;

import com.batulo.entity.Sondage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SondageRepository extends JpaRepository<Sondage, Long>, JpaSpecificationExecutor<Sondage> {

    /** Toujours filtré par entreprise : un sondage d'une autre entreprise est introuvable (404). */
    @EntityGraph(attributePaths = "creePar")
    Optional<Sondage> findByIdAndEntrepriseId(Long id, Long entrepriseId);

    /** Liste filtrée par {@link SondageSpecifications}, avec le créateur chargé. */
    @Override
    @EntityGraph(attributePaths = "creePar")
    Page<Sondage> findAll(Specification<Sondage> spec, Pageable pageable);

    /** Toutes les versions d'une lignée (la version 1 et celles qui en descendent), de la plus ancienne à la plus récente. */
    @EntityGraph(attributePaths = "creePar")
    @Query("select s from Sondage s where s.id = :ligneeId or s.lignee.id = :ligneeId order by s.version")
    List<Sondage> findLignee(Long ligneeId);

    /** Nombre de sections et de questions de plusieurs sondages en une requête : [sondageId, sections, questions]. */
    @Query("select s.id, count(distinct se.id), count(q.id) from Sondage s "
            + "left join s.sections se left join se.questions q where s.id in :ids group by s.id")
    List<Object[]> compterContenu(Collection<Long> ids);
}
