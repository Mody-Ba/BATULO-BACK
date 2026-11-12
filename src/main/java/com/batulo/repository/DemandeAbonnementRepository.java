package com.batulo.repository;

import com.batulo.entity.DemandeAbonnement;
import com.batulo.enums.StatutDemande;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DemandeAbonnementRepository extends JpaRepository<DemandeAbonnement, Long> {

    boolean existsByEntrepriseIdAndStatut(Long entrepriseId, StatutDemande statut);

    long countByStatut(StatutDemande statut);

    @EntityGraph(attributePaths = {"entreprise", "admin", "traiteePar"})
    Page<DemandeAbonnement> findByAdminId(Long adminId, Pageable pageable);

    @EntityGraph(attributePaths = {"entreprise", "admin", "traiteePar"})
    Page<DemandeAbonnement> findByStatut(StatutDemande statut, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"entreprise", "admin", "traiteePar"})
    Page<DemandeAbonnement> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"entreprise", "admin", "traiteePar"})
    Optional<DemandeAbonnement> findWithDetailsById(Long id);
}
