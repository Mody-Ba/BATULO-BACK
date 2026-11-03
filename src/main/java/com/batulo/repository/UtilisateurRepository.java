package com.batulo.repository;

import com.batulo.entity.Utilisateur;
import com.batulo.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    @EntityGraph(attributePaths = "entreprise")
    Optional<Utilisateur> findByCourriel(String courriel);

    @EntityGraph(attributePaths = "entreprise")
    Optional<Utilisateur> findWithEntrepriseById(Long id);

    boolean existsByCourriel(String courriel);

    boolean existsByCourrielAndIdNot(String courriel, Long id);

    boolean existsByRole(Role role);

    List<Utilisateur> findByRoleAndActifTrue(Role role);

    @EntityGraph(attributePaths = "entreprise")
    Optional<Utilisateur> findByIdAndRole(Long id, Role role);

    /** L'admin d'une entreprise (un seul par entreprise). */
    @EntityGraph(attributePaths = "entreprise")
    Optional<Utilisateur> findFirstByEntrepriseIdAndRole(Long entrepriseId, Role role);

    Page<Utilisateur> findByEntrepriseIdAndRole(Long entrepriseId, Role role, Pageable pageable);

    @EntityGraph(attributePaths = "entreprise")
    Optional<Utilisateur> findByIdAndEntrepriseIdAndRole(Long id, Long entrepriseId, Role role);
}
