# Premiers pas

wtWin (wtTux sous Linux) affiche et modifie un arbre généalogique webtrees comme un logiciel de généalogie classique : barre de menus, navigateur, fiche individuelle, tableaux, listes et livres. Les données se trouvent soit sur un serveur webtrees (NAS ou hébergeur web), soit directement sur cet ordinateur.

![](desktop-start.jpg)


Au premier démarrage, les deux possibilités sont proposées côte à côte.

## Se connecter à webtrees

Condition : webtrees 2.2 avec le module **api4webtrees**. C'est la personne qui gère le serveur qui installe le module ; le paquet nas4webtrees le contient déjà.

**Le plus simple, sans rien saisir :** connectez-vous à webtrees dans votre navigateur, ouvrez le menu **App** et cliquez sur **Connect with wtWin**. Le programme récupère le lien de connexion (depuis le presse-papiers ou directement depuis le navigateur), demande une seule fois confirmation et ouvre l'arbre. Ni adresse, ni mot de passe. Le lien est valable dix minutes et une seule fois.

**À la main :**

1. Saisissez l'adresse de votre site webtrees telle qu'elle s'affiche dans le navigateur. Le programme retire lui-même le reste. Si une adresse adaptée se trouve dans le presse-papiers, elle est proposée.
2. Cliquez sur **Connecter**.
3. Connectez-vous avec votre nom d'utilisateur (ou e-mail) et votre mot de passe. **Consulter sans se connecter** n'affiche que ce que voient les visiteurs du site.

> **Protection du répertoire :** si le navigateur affiche, avant webtrees, une petite fenêtre de connexion du serveur web (.htaccess), ouvrez « Le serveur demande un nom d'utilisateur et un mot de passe avant webtrees » sur l'écran d'adresse et saisissez-y ces identifiants. Le programme les envoie avec chaque requête à ce serveur ; vous vous connectez ensuite comme d'habitude. Lorsque le serveur exige une telle connexion, les champs s'ouvrent d'eux-mêmes.

> **Rester connecté :** beaucoup de serveurs suppriment la session webtrees après une pause d’environ 24 minutes. À partir d’api4webtrees 1.30, le programme reçoit un jeton d’appareil à la connexion et s’en sert pour se reconnecter discrètement ; il ne redemande le mot de passe que si le jeton n’a pas servi pendant 90 jours, si le mot de passe a changé ou si l’appareil a été déconnecté. Les appareils connectés figurent dans webtrees au menu **App**, où chacun peut être déconnecté. **Se déconnecter** déconnecte aussi l’appareil.

Vous vous connectez avec votre compte webtrees habituel. Les mêmes droits s'appliquent que sur le site : ce que vous ne voyez pas là-bas, vous ne le voyez pas ici ; ce que vous pouvez y modifier, vous pouvez le modifier ici.

> Les adresses non chiffrées (`http://`) ne sont acceptées que sur votre réseau domestique, p. ex. `http://192.168.178.73:8095`. Hors de chez vous, le serveur doit utiliser HTTPS.

## Arbre généalogique sur cet ordinateur

À droite de l'écran d'accueil : saisissez un nom et cliquez sur **Créer l'arbre généalogique**, ou sur **Importer depuis un fichier GEDCOM …** pour venir d'un autre programme. Vous travaillez ensuite sans serveur, sans mot de passe et sans internet. Voir [Arbre généalogique sur cet ordinateur](hilfe:lokal).

## Changer d'arbre ou de serveur

- Si le serveur contient plusieurs arbres, changez d'arbre avec **Fichier › Changer d'arbre généalogique**.
- Pour un autre serveur : **Fichier › Se déconnecter**, puis **Autre adresse**. Ou cliquez sur **Connect with wtWin** sur la page **App** de l'autre serveur ; cela fonctionne aussi lorsque vous êtes connecté.

## Langue

Le programme suit la langue du système (allemand, anglais, français, néerlandais ou espagnol, sinon anglais). Choisissez une langue fixe sous **Affichage › Langue**. Les libellés fournis par le serveur (noms d'événements, liens de parenté) apparaissent dans la langue du programme.

## Pour continuer

- [La fenêtre principale](hilfe:hauptfenster) : navigateur, barre d'outils, affichage
- [Fiche individuelle et modification](hilfe:person)
- [Tableaux](hilfe:tafeln), [Listes](hilfe:listen), [Livres](hilfe:buecher)
- [Raccourcis clavier](hilfe:tasten)
