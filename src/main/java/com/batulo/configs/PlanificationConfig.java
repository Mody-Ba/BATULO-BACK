package com.batulo.configs;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Active les tâches planifiées (@Scheduled), dont la tâche quotidienne des abonnements. */
@Configuration
@EnableScheduling
public class PlanificationConfig {
}
