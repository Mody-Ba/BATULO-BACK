package com.batulo.repository;

import com.batulo.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /** Conversation « admin <-> Batulo ». */
    Optional<Conversation> findByAdminIdAndGestionnaireIsNull(Long adminId);

    Optional<Conversation> findByAdminIdAndGestionnaireId(Long adminId, Long gestionnaireId);

    @EntityGraph(attributePaths = {"admin", "admin.entreprise", "gestionnaire"})
    Optional<Conversation> findWithParticipantsById(Long id);

    /** Admin : la conversation avec Batulo et celles avec ses gestionnaires. */
    @EntityGraph(attributePaths = {"admin", "admin.entreprise", "gestionnaire"})
    Page<Conversation> findByAdminId(Long adminId, Pageable pageable);

    /** Gestionnaire : sa conversation avec son admin. */
    @EntityGraph(attributePaths = {"admin", "admin.entreprise", "gestionnaire"})
    Page<Conversation> findByGestionnaireId(Long gestionnaireId, Pageable pageable);

    /** Super admins : toutes les conversations avec Batulo. */
    @EntityGraph(attributePaths = {"admin", "admin.entreprise", "gestionnaire"})
    Page<Conversation> findByGestionnaireIsNull(Pageable pageable);
}
