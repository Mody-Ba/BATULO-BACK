package com.batulo.repository;

import com.batulo.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByDestinataireId(Long destinataireId, Pageable pageable);

    Page<Notification> findByDestinataireIdAndLue(Long destinataireId, boolean lue, Pageable pageable);

    Optional<Notification> findByIdAndDestinataireId(Long id, Long destinataireId);

    long countByDestinataireIdAndLueFalse(Long destinataireId);

    /** Marque lues toutes les notifications non lues d'un utilisateur ; renvoie leur nombre. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.lue = true where n.destinataire.id = :destinataireId and n.lue = false")
    int marquerToutesLues(Long destinataireId);
}
