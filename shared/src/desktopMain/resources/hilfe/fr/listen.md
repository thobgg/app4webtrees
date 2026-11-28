# Listes

Ouvrez la fenêtre **Créer une liste** par **Créer › Liste d'ascendance**, **Liste de descendance**, **Liste des événements** ou l'icône **Liste**. Le type de liste avec ses réglages à gauche, l'aperçu des pages finales à droite ; **Imprimer** et **Enregistrer en PDF …** en bas.

## À partir de la personne centrale

![](liste-ahnen.jpg)
![](liste-stamm.jpg)
![](liste-taufpaten.jpg)
![](liste-ahnenwertung.jpg)


- **Liste d'ascendance :** tous les ancêtres par génération avec numéros Sosa, jusqu'à 12 générations ; quiconque apparaît plusieurs fois renvoie à son premier numéro (implexe).
- **Ancêtres en bout de branche :** les ancêtres où la recherche s'arrête, selon les quatre lignées des grands-parents.
- **Lignée paternelle** et **Lignée maternelle :** la lignée du père ou de la mère génération par génération, avec les conjoints.
- **Complétude de l'ascendance :** dans quelle mesure chaque génération est recherchée, sous forme de table avec l'implexe.
- **Liste de descendance :** tous les descendants avec numéros, conjoints et mariages, jusqu'à 10 générations. Numérotation selon Saragossa, d'Aboville, Henry ou continue.
- **Descendance par génération :** combien de descendants compte chaque génération : hommes, femmes, vivants, porteurs du nom.
- **Fiche individuelle :** tous les détails de la personne centrale sur une page.

## Sur l'ensemble de l'arbre

![](liste-namen.jpg)
![](liste-orte.jpg)
![](liste-ereignisse.jpg)
![](liste-spitzenahnen.jpg)


- **Liste des événements :** naissances et décès de toutes les personnes visibles par ordre de date ; en option comme **calendrier des anniversaires** par mois et jour, avec un filtre de lieu, p. ex. pour la lettre de famille.
- **Noms de famille :** tous les noms de famille avec leur nombre, la période et les lieux les plus fréquents.
- **Lieux :** tous les lieux tels qu’ils figurent dans les événements – **lieux seulement** (événements, personnes, période), **avec les noms de famille** ou **avec les personnes** et leurs événements à cet endroit. **Détails des lieux** ajoute l’identifiant GOV et les coordonnées de la gestion des lieux, **Notes** et **Sources** celles de la fiche de lieu.
- **Familles :** tous les mariages avec la noce et le nombre d'enfants, par ordre alphabétique ou chronologique.
- **Sources renseignées :** dans quelle mesure l'arbre est sourcé – individus et événements avec une source en pourcentage, photos, par type d'événement, et qui n'a encore aucune source.
- **Professions et religions :** qui exerçait quelle profession ou appartenait à quelle religion.
- **Parrains et marraines :** tous les baptêmes avec leurs parrains et marraines par ordre de date ; avec api4webtrees 1.11 ou plus récent à partir des parrains liés et de ceux en texte libre (« liés : 2 sur 3 » indique combien ont leur propre enregistrement), sinon à partir de la note « Paten: … ».

Les listes sur l'ensemble de l'arbre ont besoin de l'arbre d'un seul tenant ; le serveur doit utiliser api4webtrees 1.9 ou plus récent. Avec l'arbre généalogique sur cet ordinateur, c'est toujours le cas.

L'aperçu montre les premières pages ; s'il y en a davantage, « … et n pages de plus dans le PDF » apparaît en dessous. Chaque page porte en bas « Créé avec wtWin le … ».

## ADN

**Créer › ADN …** ouvre la vue ADN de la personne de départ, avec quatre parties. **Lignées** montre de quels ancêtres elle peut tenir l'ADN-Y, l'ADN mitochondrial (ADNmt) ou l'ADN-X, et quels descendants peuvent le tenir d'elle. En haut, choisissez la lignée, la direction et le nombre de générations.

- **ADN-Y :** du père au fils. Pour une femme, la lignée de son père est affichée, avec la mention qu'elle n'a pas elle-même de chromosome Y.
- **ADNmt :** de la mère à tous les enfants ; seules les filles le transmettent.
- **ADN-X :** un homme tient son X de sa mère et le transmet à ses filles, une femme en tient un de chaque parent et transmet un X à tous ses enfants. Chaque génération indique combien des ancêtres X possibles figurent dans l'arbre.

Les ancêtres sont classés par génération avec leur numéro d'ascendance, les descendants en liste indentée – ce sont les personnes qui pourraient faire un test pour la lignée. Un clic fait de la personne la personne de départ dans la fenêtre principale ; **Partir de …** recalcule les lignées pour elle.

Les lignées sont déduites des liens de parenté enregistrés et ne sont pas un résultat de test ; pour l'ADN-X, « possible » ne signifie pas que de l'ADN-X est réellement partagé. Seuls les parents biologiques comptent : chez un enfant adopté ou placé et chez une personne sans sexe enregistré, la lignée s'arrête avec une mention. L'entrée de menu n'apparaît que si le module webtrees dna4webtrees est installé sur le serveur ; c'est lui qui calcule les lignées.

**Tests** montre les tests ADN et les haplogroupes enregistrés pour la personne et pour les autres personnes de la même lignée, avec une mention si des haplogroupes d'une lignée se contredisent. **Correspondances** montre les correspondances ADN enregistrées : l'ADN partagé en centimorgans, la parenté dans l'arbre avec les ancêtres communs, la quantité habituelle pour cette parenté et des remarques, par exemple si la valeur sort de la plage habituelle. **Qui pourrait tester ?** indique les personnes vivantes de la lignée Y et de la lignée maternelle. **Arbre entier** montre toutes les entrées ADN de l'arbre que vous pouvez voir. Les éditeurs saisissent un test ou une correspondance pour la personne avec **Saisir un test ADN …** et **Saisir une correspondance ADN …** en bas de la fenêtre ; le module fixe la restriction de l'entrée selon sa règle (pour les personnes vivantes, visible des seuls gestionnaires), et elle peut être modifiée dans le formulaire. Les entrées se modifient et se suppriment comme tout autre événement.
