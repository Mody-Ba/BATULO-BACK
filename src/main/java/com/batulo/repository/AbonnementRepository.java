package com.batulo.repository;

import com.batulo.entity.Abonnement;
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
public interface AbonnementRepository extends JpaRepository<Abonnement, Long>, JpaSpecificationExecutor<Abonnement>,
        AbonnementRepositoryCustom {

    @EntityGraph(attributePaths = "entreprise")
    Optional<Abonnement> findByEntrepriseId(Long entrepriseId);

    @EntityGraph(attributePaths = "entreprise")
    Optional<Abonnement> findWithEntrepriseById(Long id);

    /** Abonnements de plusieurs entreprises en une requête (liste des admins). */
    @EntityGraph(attributePaths = "entreprise")
    List<Abonnement> findByEntrepriseIdIn(Collection<Long> entrepriseIds);

    /** Liste filtrée par {@link AbonnementSpecifications}, avec l'entreprise chargée. */
    @Override
    @EntityGraph(attributePaths = "entreprise")
    Page<Abonnement> findAll(Specification<Abonnement> spec, Pageable pageable);

    /**
     * Abonnements payés d'entreprises actives pour lesquels un avis d'expiration peut encore être dû
     * (utilisé par la tâche quotidienne).
     */
    @Query("select a from Abonnement a join fetch a.entreprise e "
            + "where e.actif = true and a.dateExpiration is not null and a.notifExpirationEnvoyee = false")
    List<Abonnement> findCandidatsAlerteExpiration();
}
