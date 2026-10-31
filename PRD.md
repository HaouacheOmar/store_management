# PRD — Store Management (SaaS)

## 1. Vision
Plateforme web SaaS qui permet à **n'importe quel commerçant** de gérer son magasin : catalogue, stock, fournisseurs, remises, caisse avec ticket, suivi des ventes et du chiffre d'affaires.
Un commerçant s'inscrit → son **store** est créé et il en devient l'**ADMIN**. Les données de chaque store sont strictement isolées.

## 2. Personas & rôles

| Rôle | Qui | Résumé |
|---|---|---|
| **ADMIN** | Propriétaire du store | Accès total. Gère les utilisateurs et leurs permissions. Voit le CA. |
| **MANAGER** | Employé « haut gradé » | GUEST promu par l'admin, avec des permissions supplémentaires choisies une par une. |
| **GUEST** | Caissier | Droits restreints : encaisser et voir son propre encaissé du jour. |

### 2.1 Catalogue de permissions
Une permission = une capacité. Toute future fonctionnalité sensible ajoute une permission au catalogue : l'admin pourra ensuite l'accorder à n'importe quel utilisateur.

| Permission | Autorise |
|---|---|
| `SALE_CREATE` | Encaisser une vente, imprimer le ticket |
| `SALE_CANCEL` | Annuler une vente |
| `SALES_VIEW_ALL` | Voir les ventes de tous les caissiers |
| `PRODUCT_WRITE` | Créer / modifier / supprimer des produits |
| `CATEGORY_WRITE` | Créer / modifier / supprimer des catégories |
| `STOCK_RESTOCK` | Faire un restockage |
| `SUPPLIER_WRITE` | Créer / modifier / supprimer des fournisseurs |
| `DISCOUNT_WRITE` | Créer / modifier / supprimer des remises |
| `STATS_VIEW` | Voir le dashboard CRM (CA, graphes) |
| `USER_MANAGE` | Gérer les utilisateurs et leurs permissions |

- **ADMIN** : toutes les permissions, non retirables.
- **GUEST** (par défaut) : `SALE_CREATE`.
- **MANAGER** : `SALE_CREATE` + permissions cochées par l'admin.
- La consultation (produits, stock, fournisseurs, remises actives, notifications) est ouverte à tous les utilisateurs du store.

## 3. Modules fonctionnels

### M1 — Compte & store
| ID | User story |
|---|---|
| US-1.1 | En tant que commerçant, je m'inscris (nom du store, mon nom, email, mot de passe) : le store est créé et je suis ADMIN. |
| US-1.2 | En tant qu'utilisateur, je me connecte avec email + mot de passe. |
| US-1.3 | En tant qu'admin, je crée un compte GUEST (nom, email, mot de passe initial). |
| US-1.4 | En tant qu'admin, je désactive / réactive un utilisateur. |
| US-1.5 | En tant qu'admin, je promeus un GUEST en MANAGER et je coche ses permissions. Je peux le rétrograder. |

**Règles**
- Email unique sur toute la plateforme (le login se fait sans choisir de store).
- Un utilisateur désactivé ne peut plus se connecter ; ses ventes passées restent.
- Le store possède un et un seul ADMIN (son créateur) en v1.
- Un changement de permissions prend effet à la prochaine connexion (ou au prochain rafraîchissement du token).

### M2 — Catégories & produits
| ID | User story |
|---|---|
| US-2.1 | Je crée / renomme / supprime une catégorie. |
| US-2.2 | J'ajoute un produit : nom, catégorie, prix de vente, quantité initiale, seuil d'alerte, code (optionnel). |
| US-2.3 | Je modifie un produit (prix, catégorie, seuil…). |
| US-2.4 | Je supprime un produit. |
| US-2.5 | Je liste / recherche / filtre les produits par catégorie. |

**Règles**
- Prix > 0, 2 décimales.
- Un produit appartient à exactement une catégorie.
- Suppression **logique** : le produit disparaît du catalogue et de la caisse mais reste dans l'historique des ventes et des restockages.
- Une catégorie ne peut être supprimée que si elle ne contient plus de produit actif.

### M3 — Stock & restockage
| ID | User story |
|---|---|
| US-3.1 | Je vois la liste des **produits restants** avec leur quantité en stock. |
| US-3.2 | Je vois la liste des produits **en rupture** (stock = 0) et en **stock bas** (stock ≤ seuil). |
| US-3.3 | Je fais un **restockage** : produit, quantité, **fournisseur** obligatoire, prix d'achat unitaire (optionnel). |
| US-3.4 | Je consulte l'historique des restockages (filtrable par produit, fournisseur, date). |

**Règles**
- Le stock ne peut jamais être négatif.
- Le stock baisse uniquement par une vente et remonte par un restockage ou par l'annulation d'une vente.
- Chaque restockage conserve : qui, quand, quel fournisseur, quelle quantité.

### M4 — Fournisseurs
| ID | User story |
|---|---|
| US-4.1 | J'ajoute un fournisseur : nom, téléphone, email, adresse. |
| US-4.2 | Je modifie / supprime un fournisseur. |
| US-4.3 | Je vois les restockages faits chez un fournisseur. |

**Règles**
- Suppression logique : le fournisseur n'est plus proposé au restockage, mais l'historique garde son nom.

### M5 — Remises
| ID | User story |
|---|---|
| US-5.1 | Je crée une remise ponctuelle : pourcentage, cible (**un produit** OU **une catégorie**), date/heure de début et de fin, libellé (ex. « Soldes été »). |
| US-5.2 | Je liste les remises actives, à venir et passées. |
| US-5.3 | Je modifie / supprime une remise qui n'a pas encore commencé, ou je termine une remise en cours. |

**Règles**
- Pourcentage entier de 1 à 100.
- Une remise est **active** si `début ≤ maintenant < fin`.
- Si plusieurs remises actives touchent un produit (la sienne + celle de sa catégorie, ou plusieurs), on applique **la plus forte**. Les remises ne se cumulent pas.
- Prix remisé = `prix × (1 − % / 100)`, arrondi à 2 décimales.
- La remise appliquée est figée dans la vente : la modifier ensuite ne change rien aux ventes passées.

### M6 — Caisse (POS) & ticket
| ID | User story |
|---|---|
| US-6.1 | En tant que caissier, je recherche des produits et je les ajoute à un panier avec une quantité. |
| US-6.2 | Je vois pour chaque ligne le prix, la remise appliquée et le total de la ligne, puis le total du panier. |
| US-6.3 | Je saisis le montant reçu **en espèces** ; l'app calcule la **monnaie à rendre**. |
| US-6.4 | Je valide la vente : le stock est décrémenté et le **ticket de caisse** s'imprime. |
| US-6.5 | Je peux réimprimer le ticket d'une vente. |
| US-6.6 | En tant qu'admin (ou avec `SALE_CANCEL`), j'annule une vente : le stock est recrédité. |

**Règles**
- Paiement en espèces uniquement (v1) : montant reçu ≥ total.
- La vente est refusée si un produit n'a pas assez de stock (message qui indique le produit concerné).
- Le prix et la remise sont recalculés côté serveur au moment de la validation ; le panier affiché n'est qu'un aperçu.
- **Ticket** (format 80 mm) : nom du store, n° de ticket, date/heure, caissier, lignes (produit, qté, prix unitaire, remise %, total), total, reçu, rendu.
- Une vente annulée garde le statut `CANCELLED` : elle est visible, mais exclue de tous les totaux et graphes.

### M7 — Ventes du jour
| ID | User story |
|---|---|
| US-7.1 | Je vois les ventes du jour (tickets) avec leur montant. |
| US-7.2 | Je vois la **liste des produits vendus** aujourd'hui avec la quantité totale et le montant par produit. |
| US-7.3 | Je vois **combien de produits ont été vendus** aujourd'hui et **combien a été encaissé**. |

**Règles**
- Un GUEST ne voit que **ses** ventes ; `SALES_VIEW_ALL` donne accès à celles de tout le store.
- La « journée » est la date locale du store (fuseau horaire du store).

### M8 — Dashboard CRM (ADMIN / `STATS_VIEW`)
| ID | User story |
|---|---|
| US-8.1 | Je vois le **chiffre d'affaires** sur une période (aujourd'hui, 7 jours, mois, plage personnalisée). |
| US-8.2 | Je vois l'**encaissé du jour**, au total et par caissier. |
| US-8.3 | Je vois un **graphe des produits les plus vendus** (barres, top N). |
| US-8.4 | Je vois un **graphe des catégories les plus vendues** (même forme). |
| US-8.5 | Je filtre les graphes par période, par catégorie (graphe produits), par top N (5/10/20) et par critère (quantité ou montant). |
| US-8.6 | Je vois la courbe du CA jour par jour sur la période. |

**Règles**
- CA = somme des ventes non annulées sur la période.
- KPIs affichés : CA de la période, nombre de tickets, panier moyen, nombre de produits vendus, nombre de produits en rupture.

### M9 — Dashboard guest
| ID | User story |
|---|---|
| US-9.1 | En tant que GUEST, je vois **combien j'ai encaissé aujourd'hui** et mon nombre de tickets. |

**Règles**
- Le guest ne voit jamais le chiffre d'affaires du store ni les données des autres caissiers.

### M10 — Notifications
| ID | User story |
|---|---|
| US-10.1 | Quand un produit passe sous son seuil d'alerte ou tombe à 0, une notification « à restocker » apparaît en temps réel (cloche). |
| US-10.2 | Je marque une notification comme lue. |
| US-10.3 | Depuis la notification, j'ouvre directement le restockage du produit. |

**Règles**
- Une seule notification ouverte par produit et par type (pas de spam à chaque vente).
- Un restockage qui ramène le stock au-dessus du seuil résout la notification.
- Les destinataires sont les utilisateurs ayant `STOCK_RESTOCK` (dont l'admin).
- Le statut lu / non lu est propre à chaque utilisateur.

## 4. Exigences non fonctionnelles
- **Isolation multi-tenant** : aucun utilisateur ne peut lire ou modifier les données d'un autre store.
- **Sécurité** : mots de passe hachés (BCrypt), JWT à durée limitée, contrôle des permissions côté serveur (l'UI masque, le serveur interdit).
- **Cohérence du stock** : deux ventes simultanées ne peuvent pas survendre un produit.
- **Disponibilité** : la panne du reporting n'empêche pas d'encaisser.
- **Performance** : validation d'une vente < 1 s ; dashboard < 2 s.
- **UI** : en français, responsive (la caisse doit être utilisable sur tablette).
- **Montants** en décimal exact (jamais en float). Une seule devise par store.

## 5. Hors périmètre v1
Paiement par carte / terminal bancaire, multi-devise, plusieurs admins par store, commandes fournisseurs (bons de commande), notifications par email, application mobile native, Kubernetes, gestion des retours partiels.

## 6. Traçabilité du besoin initial
| Besoin exprimé | Couvert par |
|---|---|
| Ajouter / supprimer des produits, catégories | M2 |
| Suivre le stock, produits restants, ruptures | M3 |
| Restocker en précisant le fournisseur | M3 (US-3.3) |
| Ajouter / supprimer des fournisseurs | M4 |
| Prix, remises ponctuelles par produit ou catégorie | M2, M5 |
| Ventes du jour, produits vendus, encaissé du jour | M7 |
| Notification de restockage | M10 |
| Ticket de caisse imprimé | M6 |
| Admin, guest, haut gradé avec privilèges évolutifs | §2, M1 |
| CRM : CA, encaissé du jour | M8 |
| Guest : son encaissé du jour (sans CA) | M9 |
| Graphes produits / catégories filtrables | M8 |
