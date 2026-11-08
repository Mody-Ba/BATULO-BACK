package com.batulo.repository;

import com.batulo.entity.EnvoiStatistique;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EnvoiStatistiqueRepository extends JpaRepository<EnvoiStatistique, Long> {

    @EntityGraph(attributePaths = "sondage")
    Page<EnvoiStatistique> findByAdminId(Long adminId, Pageable pageable);

    @EntityGraph(attributePaths = "sondage")
    Page<EnvoiStatistique> findByGestionnaireId(Long gestionnaireId, Pageable pageable);

    @EntityGraph(attributePaths = "sondage")
    Optional<EnvoiStatistique> findByIdAndAdminId(Long id, Long adminId);
}
