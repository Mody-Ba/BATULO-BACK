package com.batulo.repository;

import com.batulo.entity.Utilisateur;
import com.batulo.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long>, JpaSpecificationExecutor<Utilisateur> {

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

    /** Comptes d'un rôle, avec leur entreprise (liste paginée du super admin). */
    @EntityGraph(attributePaths = "entreprise")
    Page<Utilisateur> findByRole(Role role, Pageable pageable);

    /** Admins de plusieurs entreprises en une requête (liste des abonnements). */
    List<Utilisateur> findByEntrepriseIdInAndRole(Collection<Long> entrepriseIds, Role role);

    long countByEntrepriseIdAndRole(Long entrepriseId, Role role);

    /** Comptes d'un rôle dans une entreprise, actifs et désactivés, en une requête : [actif, nombre]. */
    @Query("select u.actif, count(u) from Utilisateur u where u.entreprise.id = :entrepriseId and u.role = :role "
            + "group by u.actif")
    List<Object[]> compterParActivation(Long entrepriseId, Role role);

    @EntityGraph(attributePaths = "entreprise")
    Optional<Utilisateur> findByIdAndEntrepriseIdAndRole(Long id, Long entrepriseId, Role role);
}
