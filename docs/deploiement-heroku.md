# Déploiement du backend sur Heroku

Backend Spring Boot sur Heroku (buildpack Java + Heroku Postgres), frontend Angular sur Firebase Hosting.
Le profil Spring `prod` est activé par le `Procfile` ; toutes les valeurs viennent des variables d'environnement
Heroku (« config vars »). `.env.properties` n'est jamais lu en prod.

Toutes les valeurs d'exemple ci-dessous sont **fictives**.

## Ce que fait le profil prod

- Au démarrage, **avant** de se connecter à la base, l'application vérifie les variables : s'il en manque une ou
  qu'une valeur est invalide, elle s'arrête avec « APPLICATION FAILED TO START » et la liste de **toutes** les
  variables à corriger (sans afficher les secrets). Contrôles : présence, ports numériques, secrets de 32 octets au
  moins, JWT_SECRET différent d'INVITATION_SECRET, adresses en `https://` et jamais `localhost`, clés Stripe **de test**
  (`sk_test_` / `pk_test_` : le mode live n'est pas encore pris en charge), prix des sondages, profils `prod` et `dev`
  jamais actifs ensemble.
- Base : `JDBC_DATABASE_URL`, `JDBC_DATABASE_USERNAME`, `JDBC_DATABASE_PASSWORD` (fournis automatiquement par le
  buildpack Java quand Heroku Postgres est attaché) ; à défaut, `DATABASE_URL` est converti (SSL exigé). Liquibase
  crée ou met à jour le schéma à chaque démarrage, puis Hibernate le valide (`ddl-auto=validate`).
- Routes `/api/dev/**` absentes (profil dev seulement), Swagger et OpenAPI désactivés, erreurs sans détail technique.
- CORS limité à `CORS_ORIGINS`. En-têtes `X-Forwarded-For` / `X-Forwarded-Proto` du routeur Heroku pris en compte
  (adresse réelle du client pour la limite par IP, https reconnu) ; une requête en `http://` est redirigée vers
  `https://`, et l'en-tête HSTS est envoyé.

## Variables d'environnement

### Fournies par Heroku (ne pas les définir)

| Nom | Rôle |
|---|---|
| `PORT` | Port d'écoute attribué au dyno (`server.port`). |
| `DATABASE_URL` | Ajoutée par Heroku Postgres. |
| `JDBC_DATABASE_URL`, `JDBC_DATABASE_USERNAME`, `JDBC_DATABASE_PASSWORD` | Calculées par le buildpack Java au démarrage du dyno à partir de `DATABASE_URL`. |

### Obligatoires

| Nom | Rôle | Exemple fictif |
|---|---|---|
| `JWT_SECRET` | Clé de signature des jetons de connexion (HS256), 32 octets minimum. | `openssl rand -base64 48` |
| `INVITATION_SECRET` | Clé HMAC des liens d'invitation, **différente** de JWT_SECRET, 32 octets minimum. La changer invalide les liens déjà envoyés. | `openssl rand -base64 48` |
| `CORS_ORIGINS` | Origines du frontend autorisées, séparées par des virgules, en https, jamais `*`. | `https://batulo-exemple.web.app,https://batulo-exemple.firebaseapp.com` |
| `APP_BASE_URL` | Adresse publique de l'API (URL absolue des logos dans le formulaire public). | `https://batulo-api-exemple.herokuapp.com` |
| `PUBLIC_FORM_URL` | Page du formulaire répondant (frontend) ; le jeton du sondage y est ajouté (liens, QR codes, invitations). | `https://batulo-exemple.web.app/s` |
| `RESET_PASSWORD_URL` | Page du frontend qui reçoit le jeton de réinitialisation du mot de passe. | `https://batulo-exemple.web.app/reinitialiser-mot-de-passe` |
| `MOOSYL_URL_RETOUR` | Page du frontend où Moosyl renvoie l'admin après un paiement mobile. | `https://batulo-exemple.web.app/admin/abonnement/paiement-mobile` |
| `MAIL_HOST` | Serveur SMTP du fournisseur choisi. | `smtp.fournisseur-exemple.com` |
| `MAIL_PORT` | Port SMTP (587 avec STARTTLS en général). | `587` |
| `MAIL_USERNAME` | Identifiant SMTP (obligatoire sauf si `MAIL_SMTP_AUTH=false`). | `apikey-exemple` |
| `MAIL_PASSWORD` | Mot de passe ou clé SMTP (obligatoire sauf si `MAIL_SMTP_AUTH=false`). | `cle-smtp-fictive` |
| `MAIL_FROM` | Expéditeur des courriels (adresse vérifiée chez le fournisseur). | `Batulo <no-reply@exemple.com>` |
| `STRIPE_SECRET_KEY` | Clé secrète Stripe **de test**. | `sk_test_fictive` |
| `STRIPE_PUBLISHABLE_KEY` | Clé publique Stripe **de test**, renvoyée au frontend. | `pk_test_fictive` |
| `MOOSYL_API_KEY` | Clé secrète Moosyl (sandbox ou production : l'environnement dépend de la clé). | `cle-moosyl-fictive` |
| `MOOSYL_WEBHOOK_SECRET` | Secret choisi à la création du webhook dans le tableau de bord Moosyl. | `secret-webhook-fictif` |
| `PRIX_SONDAGE_CAD` | Prix d'UN sondage payé par carte (devise `STRIPE_CURRENCY`, 2 décimales max). | `0.50` |
| `PRIX_SONDAGE_MRU` | Prix d'UN sondage payé par mobile, en MRU entiers. | `20` |

### Facultatives

| Nom | Rôle | Défaut |
|---|---|---|
| `SUPER_ADMIN_EMAIL`, `SUPER_ADMIN_PASSWORD` | Création du super admin au premier démarrage (voir plus bas), à retirer ensuite. Absentes : simple avertissement. | — |
| `MAIL_SMTP_AUTH` | Authentification SMTP. | `true` |
| `MAIL_STARTTLS` | STARTTLS activé et exigé. | `true` |
| `STRIPE_CURRENCY` | Devise des paiements par carte. | `cad` |
| `STRIPE_WEBHOOK_SECRET` | Réservé au futur webhook Stripe (inutilisé aujourd'hui). | — |
| `MOOSYL_BASE_URL` | API Moosyl. | `https://api.moosyl.com` |
| `MOOSYL_PRIX_MENSUEL_MRU`, `MOOSYL_PRIX_ANNUEL_MRU` | Prix des abonnements par mobile (MRU). | `40`, `80` |
| `JWT_ACCESS_MINUTES`, `JWT_REFRESH_DAYS` | Durée des jetons d'accès et de rafraîchissement. | `15`, `7` |
| `INVITATION_ENVOIS_PAR_HEURE` | Courriels d'invitation par entreprise et par heure. | `500` |
| `PUBLIC_RATE_GET`, `PUBLIC_RATE_POST` | Limite des routes publiques, par IP et par minute. | `120`, `20` |
| `BATULO_FUSEAU_HORAIRE` | Fuseau des dates dans les courriels et de la tâche quotidienne. | `America/Toronto` |
| `ABONNEMENT_CRON` | Tâche quotidienne (rappels, expirations). | `0 0 8 * * *` |
| `BATULO_COULEUR_DEFAUT` | Couleur par défaut des formulaires. | `#005CBB` |
| `DB_POOL_MAX` | Connexions au plus vers la base (Heroku Postgres essential-0 : 20 au total). | `10` |
| `JAVA_OPTS` | Options de la JVM (le buildpack règle déjà la mémoire selon le dyno). | — |

Ne pas définir `TRUST_PROXY` ni `SPRING_PROFILES_ACTIVE=dev` en prod (le `Procfile` impose `prod` ; `prod` et `dev`
ensemble sont refusés au démarrage).

## Commandes, dans l'ordre

Prérequis : [Heroku CLI](https://devcenter.heroku.com/articles/heroku-cli) installée, `heroku login` fait, depuis la
racine du dépôt. Remplacer `batulo-api-exemple` par le nom choisi.

```bash
# 1. Création de l'application (buildpack Java : Maven Wrapper, Java 21 d'après system.properties)
heroku create batulo-api-exemple
heroku buildpacks:set heroku/java -a batulo-api-exemple

# 2. Base Heroku Postgres (DATABASE_URL est ajoutée automatiquement)
heroku addons:create heroku-postgresql:essential-0 -a batulo-api-exemple
heroku pg:wait -a batulo-api-exemple

# 3. Variables (une commande par groupe ; valeurs fictives à remplacer)
heroku config:set -a batulo-api-exemple \
  JWT_SECRET="$(openssl rand -base64 48)" \
  INVITATION_SECRET="$(openssl rand -base64 48)"

heroku config:set -a batulo-api-exemple \
  APP_BASE_URL=https://batulo-api-exemple.herokuapp.com \
  CORS_ORIGINS=https://batulo-exemple.web.app,https://batulo-exemple.firebaseapp.com \
  PUBLIC_FORM_URL=https://batulo-exemple.web.app/s \
  RESET_PASSWORD_URL=https://batulo-exemple.web.app/reinitialiser-mot-de-passe \
  MOOSYL_URL_RETOUR=https://batulo-exemple.web.app/admin/abonnement/paiement-mobile

heroku config:set -a batulo-api-exemple \
  MAIL_HOST=smtp.fournisseur-exemple.com \
  MAIL_PORT=587 \
  MAIL_USERNAME=apikey-exemple \
  MAIL_PASSWORD=cle-smtp-fictive \
  MAIL_FROM="Batulo <no-reply@exemple.com>"

heroku config:set -a batulo-api-exemple \
  STRIPE_SECRET_KEY=sk_test_fictive \
  STRIPE_PUBLISHABLE_KEY=pk_test_fictive \
  STRIPE_CURRENCY=cad \
  MOOSYL_API_KEY=cle-moosyl-fictive \
  MOOSYL_WEBHOOK_SECRET=secret-webhook-fictif \
  PRIX_SONDAGE_CAD=0.50 \
  PRIX_SONDAGE_MRU=20

# Vérifier la liste (les valeurs s'affichent : à faire sur un écran privé)
heroku config -a batulo-api-exemple

# 4. Déploiement : Heroku construit le jar (./mvnw, tests sautés) puis lance le Procfile
heroku git:remote -a batulo-api-exemple
git push heroku main
# (avant la fusion de la branche : git push heroku feature/deploiement-heroku:main)

# 5. Dyno web et journaux
heroku ps:scale web=1 -a batulo-api-exemple
heroku logs --tail -a batulo-api-exemple
```

Une commande `heroku config:set` redémarre l'application : si une variable manque encore, les journaux affichent
« APPLICATION FAILED TO START » et la liste des variables à corriger.

Dyno : prendre au moins **Basic** (un dyno Eco s'endort après 30 minutes sans trafic : la tâche quotidienne ne
passerait pas et le premier appel attendrait le démarrage). Heroku laisse 60 secondes pour ouvrir le port ; Liquibase
et Spring démarrent en général en 20 à 40 secondes.

## Vérification

```bash
API=https://batulo-api-exemple.herokuapp.com

# Démarrage : la dernière ligne utile des journaux est « Started BatuloApplication »
heroku logs -n 200 -a batulo-api-exemple | grep -E "Started BatuloApplication|APPLICATION FAILED"

# API publique : 200 et en-tête Strict-Transport-Security
curl -i $API/api/abonnements/formules

# http redirigé vers https : 302, Location: https://...
curl -i http://batulo-api-exemple.herokuapp.com/api/abonnements/formules

# Swagger et OpenAPI désactivés : 401 (aucune documentation servie)
curl -i $API/swagger-ui.html
curl -i $API/v3/api-docs

# Routes de dev absentes : 401 sans jeton ; 404 INTROUVABLE avec un jeton admin
curl -i -X POST $API/api/dev/paiements/carte-test

# CORS : 200 et Access-Control-Allow-Origin pour le frontend ; 403 pour une autre origine
curl -i -X OPTIONS $API/api/abonnements/formules \
  -H "Origin: https://batulo-exemple.web.app" -H "Access-Control-Request-Method: GET"
curl -i -X OPTIONS $API/api/abonnements/formules \
  -H "Origin: https://autre-site.example" -H "Access-Control-Request-Method: GET"

# Schéma : changesets Liquibase appliqués (024-... compris)
heroku pg:psql -a batulo-api-exemple -c "select id from databasechangelog order by orderexecuted desc limit 5"
```

## Super admin : le créer puis retirer le mot de passe

Le super admin (propriétaire de Batulo) est créé une seule fois, au démarrage, s'il n'en existe aucun en base. Aucune
route de l'API ne permet d'en créer un.

1. Définir les deux variables (la commande redémarre l'application). Pour ne pas laisser le mot de passe dans
   l'historique du terminal, le saisir sans l'afficher :
   ```bash
   read -s MDP_SUPER_ADMIN
   heroku config:set -a batulo-api-exemple SUPER_ADMIN_EMAIL=proprietaire@exemple.com \
     SUPER_ADMIN_PASSWORD="$MDP_SUPER_ADMIN"
   unset MDP_SUPER_ADMIN
   ```
2. Vérifier dans les journaux : `Super admin créé à partir de SUPER_ADMIN_EMAIL : compte ...`
   ```bash
   heroku logs -n 200 -a batulo-api-exemple | grep "Super admin"
   ```
3. Se connecter une fois depuis le frontend avec ce compte.
4. Retirer le mot de passe (et l'adresse) des variables Heroku :
   ```bash
   heroku config:unset -a batulo-api-exemple SUPER_ADMIN_PASSWORD SUPER_ADMIN_EMAIL
   ```
   Au redémarrage suivant, l'application écrit `Super admin déjà présent en base : aucune modification.` ; le compte
   en base n'est jamais modifié par ces variables. (Sans super admin en base et sans les variables, elle écrit un
   simple avertissement et démarre.)

## Réglages chez les fournisseurs

- **Moosyl** : webhook `https://batulo-api-exemple.herokuapp.com/api/webhooks/moosyl`, avec le même secret que
  `MOOSYL_WEBHOOK_SECRET`.
- **Firebase Hosting** : l'adresse de l'API (`APP_BASE_URL`) dans l'environnement de production du frontend ; les
  domaines Firebase (et un éventuel domaine personnalisé) dans `CORS_ORIGINS`.
- **Fournisseur SMTP** : domaine ou adresse de `MAIL_FROM` vérifiés (SPF, DKIM), sinon les courriels partent en spam.
- **Stripe** : mode test seulement pour ce premier déploiement. Un paiement par carte n'est pris en compte que si le
  frontend appelle `POST /api/abonnements/paiement/confirmer` ; le webhook Stripe vérifié et le mode live viendront
  ensemble (voir « À traiter » dans CLAUDE.md).

## Limites connues

- Une seule instance (un dyno web) : la limite de requêtes par IP et la file d'envoi des invitations sont en mémoire.
- Les logos sont en base (BYTEA) : rien n'est écrit sur le disque éphémère du dyno.
