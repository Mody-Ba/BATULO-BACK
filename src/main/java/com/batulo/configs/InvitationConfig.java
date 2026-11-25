package com.batulo.configs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Exécuteur des courriels d'invitation : envoyés en arrière-plan (2 fils, file en mémoire : une seule instance),
 * sans bloquer la requête. batulo.invitations.envoi-synchrone=true (tests) envoie dans le fil de la requête, après
 * la validation de la transaction, pour des vérifications déterministes.
 */
@Configuration
public class InvitationConfig {

    public static final String EXECUTEUR = "envoiInvitationsExecutor";

    @Bean(name = EXECUTEUR)
    public TaskExecutor envoiInvitationsExecutor(
            @Value("${batulo.invitations.envoi-synchrone:false}") boolean synchrone) {
        if (synchrone) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executeur = new ThreadPoolTaskExecutor();
        executeur.setCorePoolSize(2);
        executeur.setMaxPoolSize(2);
        executeur.setThreadNamePrefix("invitations-");
        // Arrêt de l'application : les envois en cours se terminent, ceux en file restent « à inviter ».
        executeur.setWaitForTasksToCompleteOnShutdown(true);
        executeur.setAwaitTerminationSeconds(30);
        executeur.initialize();
        return executeur;
    }
}
