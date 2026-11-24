# Questions, erreurs, confidentialité

## Signaler questions et erreurs

![](desktop-hilfe.jpg)


wtWin, wtTux, wtMac et wtAnd ne sont pas des produits officiels de webtrees. Merci de ne pas poser vos questions sur le forum de webtrees, mais d'ouvrir un ticket sur **github.com/thobgg/app4webtrees/issues**. Utile : la version indiquée dans **Aide › À propos de wtWin** (avec le numéro de build), Windows ou Linux, et ce que vous faisiez quand c'est arrivé. Pour l'arbre généalogique sur cet ordinateur, joignez aussi le fichier `php.log` (voir [Arbre généalogique sur cet ordinateur](hilfe:lokal)).

Les nouvelles versions paraissent sur **github.com/thobgg/app4webtrees/releases**. Le fichier Windows n'est pas signé, Windows avertit donc d'un éditeur inconnu au premier démarrage (« Informations complémentaires », puis « Exécuter quand même »).

## Ce que le programme enregistre

- L'adresse du serveur, le nom d'utilisateur et le cookie de session, **jamais le mot de passe webtrees**. Seuls les identifiants d'une protection du répertoire (si saisis) restent sur le PC : ils doivent accompagner chaque requête.
- À partir d’api4webtrees 1.30, un jeton d’appareil pour se reconnecter discrètement. Il change à chaque utilisation ; un jeton dérobé devient ainsi inutile et bloque l’appareil sur le serveur.
- Vos réglages (disposition, apparence, réglages des tableaux et des listes, règles de couleur, liste des coches de la vérification) sur cet ordinateur.
- Pour l'arbre généalogique sur cet ordinateur : l'arbre entier dans le dossier `app4webtrees`, y compris l'accès au webtrees local.
- Pas de statistiques d'utilisation, pas de publicité, aucune transmission à des tiers.

## Confidentialité dans l'arbre

Le programme se connecte avec votre compte webtrees ; chaque requête s'exécute en tant que cet utilisateur. Les mêmes règles s'appliquent que sur le site : les personnes vivantes, les enregistrements verrouillés et les arbres privés ne sont visibles que si webtrees le permet. Les sorties (tableaux, listes, livres, PDF) ne contiennent que ce que vous êtes autorisé à voir.

## Sans chiffrement sur le réseau domestique

Une adresse `http://` n'est acceptée que sur le réseau domestique (adresses privées comme 192.168…, noms comme `diskstation` ou `.local`). La connexion n'est alors pas chiffrée, ce qui convient à la maison. Pour accéder à l'arbre en déplacement, le serveur doit utiliser HTTPS ; nas4webtrees fournit les instructions.

## Téléphone et tablette

Pour Android, il existe **wtAnd**, de la même famille et avec les mêmes données : arbre, photos, modification, anniversaires. Un code QR sur la page **App** de webtrees connecte le téléphone sans rien saisir. wtAnd a besoin d'un serveur ; l'arbre généalogique sur cet ordinateur n'est accessible que depuis l'ordinateur.

## Recherche de mises à jour

Une fois par jour, au démarrage, le programme demande à `wtwin.bgg-home.de` le numéro de version actuel – un petit fichier texte, rien d’autre. Rien n’est envoyé sauf cette requête : pas d’arbre, pas d’identifiant, pas d’usage. S’il existe une version plus récente, la barre d’état indique « La version 1.52 est disponible – Télécharger » ; un clic ouvre la page de téléchargement, × ignore cette version. Sans réseau, rien ne se passe. Désactiver : **Aide › Rechercher des mises à jour** ; vérifier à la main : **Aide › Rechercher des mises à jour maintenant …**. Une mise à jour s’installe simplement par-dessus ; réglages, connexion et arbre sur ce PC restent.

## Licence

wtWin est distribué sous licence GPL-3, comme webtrees. L'arbre généalogique sur cet ordinateur contient webtrees inchangé par rapport à la version officielle (webtrees.net).

## Manuel complet

Avec des images et des remarques à jour : **wtwin.bgg-home.de** (bouton en bas de cette fenêtre).
