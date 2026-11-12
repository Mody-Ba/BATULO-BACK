package com.batulo.enums;

/** Côté d'une conversation : un message envoyé par un côté est « reçu » par l'autre. */
public enum CoteConversation {
    ADMIN,
    GESTIONNAIRE,
    /** Batulo : les super admins, qui partagent la même boîte. */
    PLATEFORME
}
