package com.batulo.repository;

import com.batulo.entity.Message;
import com.batulo.enums.CoteConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Un message est « reçu » par le côté qui ne l'a pas envoyé (cote <> côté du lecteur). */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByConversationId(Long conversationId, Pageable pageable);

    @Query("select count(m) from Message m where m.conversation.id = :conversationId and m.luLe is null "
            + "and m.cote <> :cote")
    long nonLus(Long conversationId, CoteConversation cote);

    /** Non lus par conversation, pour la liste : [conversationId, nombre]. */
    @Query("select m.conversation.id, count(m) from Message m where m.conversation.id in :conversations "
            + "and m.luLe is null and m.cote <> :cote group by m.conversation.id")
    List<Object[]> nonLusParConversation(Collection<Long> conversations, CoteConversation cote);

    /** Dernier message de chaque conversation. */
    @Query("select m from Message m where m.id in (select max(m2.id) from Message m2 "
            + "where m2.conversation.id in :conversations group by m2.conversation.id)")
    List<Message> derniersMessages(Collection<Long> conversations);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Message m set m.luLe = :maintenant where m.conversation.id = :conversationId and m.luLe is null "
            + "and m.cote <> :cote")
    int marquerLus(Long conversationId, CoteConversation cote, Instant maintenant);

    @Query("select count(m) from Message m where m.conversation.admin.id = :adminId and m.luLe is null "
            + "and m.cote <> com.batulo.enums.CoteConversation.ADMIN")
    long nonLusAdmin(Long adminId);

    @Query("select count(m) from Message m where m.conversation.gestionnaire.id = :gestionnaireId and m.luLe is null "
            + "and m.cote <> com.batulo.enums.CoteConversation.GESTIONNAIRE")
    long nonLusGestionnaire(Long gestionnaireId);

    @Query("select count(m) from Message m where m.conversation.gestionnaire is null and m.luLe is null "
            + "and m.cote <> com.batulo.enums.CoteConversation.PLATEFORME")
    long nonLusPlateforme();
}
