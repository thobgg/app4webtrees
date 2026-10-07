# Livres

**Créer › Créer un livre …** transforme l'arbre en un livre dans le style des livres de familles imprimés : chaque personne avec événements, sources, parrains et marraines et notes, renvois vers les parents et les enfants, portraits en marge, table des matières et index.

## Trois livres

- **Livre d'ascendance :** tous les ancêtres de la personne centrale par génération et numéro Sosa, avec dates, baptêmes, inhumations, sources et notes. Les quatre lignées des grands-parents en couleur dans la marge, en option.
- **Livre de descendance :** tous les descendants génération par génération, avec conjoints, enfants et renvois ; numéros selon Saragossa, d'Aboville, Henry ou continus ; couleurs de branche pour chaque enfant du couple souche.
- **Livre de familles :** une entrée par famille, par ordre alphabétique ou chronologique. Avec un **filtre de lieu**, il devient un livre des familles d'une commune. Nécessite l'arbre entier d'un seul tenant (api4webtrees 1.9 ou plus récent sur le serveur). **Maisons et fermes** (à partir d’api4webtrees 1.15) ajoute une partie sur les bâtiments : chaque ferme et maison du lieu issue de la gestion des lieux (fiches de lieu avec un type sous le lieu), avec son histoire et ses habitants et propriétaires par ordre chronologique, chacun renvoyant à sa famille ; les familles renvoient à leur maison (H1, H2 …). Ce qui compte comme maison dépend du type de la fiche de lieu (maison, ferme, moulin, église … ; sans type, un numéro de maison dans le nom suffit) ; quartiers et villages deviennent des chapitres, les lieux habités qui ne sont pas des bâtiments vont dans l’annexe « Autres lieux ». Si la fiche de lieu porte un numéro de type GOV (GEDCOM-L `2 _GOVTYPE`, api4webtrees 1.18.1 ou plus), ce numéro décide avant le texte : ferme, bâtiment, moulin, domaine, église … sont des maisons, village, quartier, commune … des niveaux supérieurs. Les options « Seulement maisons et fermes » et « Inclure les lieux sans type » règlent cela. Les noms de lieux peuvent être séparés par des virgules ou des points-virgules.

## Réglages

- **Données :** générations (2 à 12), notes, sources, abréger les noms de lieux, afficher en entier les ancêtres en double (au lieu de « voir n° »).
- **Présentation :** images, code couleur, préface (votre propre texte sur la première page), tableau en page dépliante (A3, PDF seulement).
- **Index :** noms, lieux, professions, sources, chacun renvoyant aux numéros des entrées.

## Enregistrer

**Enregistrer le livre** demande le format :

- **PDF** avec signets et liens (un clic sur « voir n° » saute à l'entrée).
- **DOCX** pour continuer le travail dans Word ou LibreOffice. Mettez-y une fois à jour la table des matières : cliquez dessus et appuyez sur F9 (LibreOffice : Outils › Actualiser › Index et tables).
- **HTML** pour un site web, **TeX** pour la composition avec LaTeX, **Texte**.

Pour les grands arbres, le chargement des personnes et des images prend un moment ; la fenêtre affiche la progression.

## Grands arbres avec beaucoup d’images

webtrees calcule chaque vignette sur le serveur au premier appel – avec des milliers de personnes, le premier passage prend du temps. wtWin ne charge que l’image principale par personne, six à la fois, affiche la progression (« Images 2 340/8 900 – de ce PC …, cache …, serveur … ») et peut être arrêté avec **Continuer sans les images restantes**. Ce qui a été chargé une fois reste dans le cache de ce PC (`~/.cache/app4webtrees/medien/<serveur>/<arbre>/`, sous Windows dans le dossier des données d’application) ; le passage suivant et les tableaux n’ont plus besoin du serveur. Le dialogue du livre montre la taille du cache et **Vider le cache des images** sous Présentation.

Si vous gardez une copie du dossier des médias de webtrees sur ce PC (par exemple parce que vous téléversez par FTP), indiquez-la comme **Dossier des médias sur ce PC** : les images sont alors lues là et réduites localement, sans serveur. Les autres laissent le champ vide. webtrees reste toujours la source ; le cache et le dossier ne sont que des copies.
