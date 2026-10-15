# Boeken

**Maken › Boek maken …** maakt van de stamboom een boek in de stijl van gedrukte familieboeken: elke persoon met gebeurtenissen, bronnen, doopgetuigen en notities, verwijzingen naar ouders en kinderen, portretten in de marge, inhoudsopgave en registers.

## Drie boeken

![](buch-vor-titel.jpg)
![](buch-vor-gen.jpg)
![](buch-nach-gen.jpg)
![](buch-fam.jpg)


- **Voorouderboek:** alle voorouders van de proband per generatie en Kekulé-nummer, met data, dopen, begrafenissen, bronnen en notities. De vier grootouderlijnen desgewenst gekleurd in de marge.
- **Nakomelingenboek:** alle nakomelingen generatie voor generatie, met echtgenoten, kinderen en verwijzingen; nummers volgens Saragossa, d'Aboville, Henry of doorlopend; takkleuren per kind van het stampaar.
- **Familieboek:** één vermelding per gezin, alfabetisch of chronologisch. Met **opbouw „per persoon”** wordt het het personenrapport: één vermelding per persoon met ouders, huwelijken en kinderen als verwijzingen, registers op persoonsnummers. Ook één vermelding per gezin, alfabetisch of chronologisch. Met een **plaatsfilter** wordt het een plaatselijk familieboek. Vereist de hele stamboom in één geheel (api4webtrees 1.9 of nieuwer op de server). **Huizen en boerderijen** (vanaf api4webtrees 1.15) voegt een deel over de gebouwen toe: elke boerderij en elk huis van de plaats uit het plaatsenbeheer (locatierecords met een soort onder de plaats) met de geschiedenis en de bewoners en eigenaren op volgorde van tijd, elk met verwijzing naar het gezin; de gezinnen verwijzen terug naar hun huis (H1, H2 …). Wat een huis is, bepaalt de soort in het locatierecord (huis, boerderij, molen, kerk …; zonder soort telt een huisnummer in de naam); stadsdelen en dorpen worden hoofdstukken, bewoonde plaatsen die geen gebouw zijn, staan in de bijlage „Overige plaatsen”. Draagt het locatierecord een GOV-typenummer (GEDCOM-L `2 _GOVTYPE`, api4webtrees 1.18.1 of later), dan beslist dat nummer vóór de tekst: boerderij, gebouw, molen, landgoed, kerk … zijn huizen, dorp, stadsdeel, gemeente … niveaus erboven. De vinkjes „Alleen huizen en boerderijen” en „Plaatsen zonder soort opnemen” regelen dit. Plaatsnamen mogen met komma of puntkomma zijn gescheiden.

## Instellingen

- **Gegevens:** generaties (2 tot 12), notities, bronnen, plaatsnamen inkorten, dubbele voorouders volledig weergeven (in plaats van „zie nr.“).
- **Levenden:** voor publicatie verschijnen levenden volledig, alleen met naam of helemaal niet; levend is wie webtrees als levend ziet of wie jonger is dan de leeftijdsgrens (80 tot 120 jaar). Alleen familieboek en personenrapport.
- **Voorwoord uit bestand …** leest een tekstbestand in het voorwoord; **Sjabloon opslaan …/laden …** bewaart boeksoort en alle instellingen als `.wtbuch`-bestand, bijvoorbeeld per plaats.
- **Weergave:** afbeeldingen, kleurcodering, voorwoord (eigen tekst op de eerste pagina), schema als uitklappagina (A3, alleen PDF).
- **Registers:** namen, plaatsen, beroepen, bronnen, elk met verwijzing naar de nummers van de vermeldingen.

## Opslaan

![](buch-vor-reg.jpg)
![](buch-nach-titel.jpg)


**Boek opslaan** vraagt naar het formaat:

- **PDF** met bladwijzers en links (een klik op „zie nr.“ springt naar de vermelding).
- **DOCX** om verder te bewerken in Word of LibreOffice. Werk daar de inhoudsopgave één keer bij: erop klikken en F9 drukken (LibreOffice: Extra › Bijwerken › Indexen en tabellen).
- **Website:** een map om te uploaden – startpagina met voorwoord en tegels, alle vermeldingen op één pagina met letterbalk, elke vermelding ook als eigen pagina met bladeren, huizen, registers als pagina’s, stylesheet en afbeeldingen als bestanden. Zet levenden eerst op „alleen naam”.
- **HTML** voor een website, **TeX** voor zetwerk met LaTeX, **Tekst**.

Bij grote stambomen duurt het laden van personen en afbeeldingen even; het venster toont de voortgang.

## Grote stambomen met veel afbeeldingen

webtrees berekent elke miniatuur bij de eerste opvraging op de server – bij duizenden personen duurt de eerste keer dus even. wtWin laadt per persoon alleen de hoofdafbeelding, zes tegelijk, toont de voortgang („Afbeeldingen 2.340/8.900 – van deze pc …, cache …, server …”) en kan met **Doorgaan zonder de overige afbeeldingen** worden gestopt. Wat eenmaal geladen is, blijft in de cache op deze pc (`~/.cache/app4webtrees/medien/<server>/<stamboom>/`, onder Windows in de map met toepassingsgegevens); de volgende keer en de tabellen daarna hebben niets meer van de server nodig. Het boekvenster toont onder Weergave de grootte van de cache en **Afbeeldingencache legen**.

Wie een kopie van de webtrees-mediamap op deze pc heeft (bijvoorbeeld omdat hij via FTP uploadt), vult die in als **Mediamap op deze pc**: afbeeldingen worden dan daar gelezen en lokaal verkleind, zonder server. Alle anderen laten het veld leeg. webtrees blijft altijd de bron; cache en map zijn alleen kopieën.

**Zichtbaarheid:** kies voor wie het boek is: alles (weergave van de beheerder), wat leden mogen zien of wat bezoekers mogen zien. Het boek laat dan weg wat webtrees voor deze lezer verbergt – beperkte gebeurtenissen („Tonen aan leden”, „Tonen aan beheerders”), gegevens onder de privacyregels van de stamboom, peetouders en gezinnen die hij niet mag zien, en de levensgegevens van levende personen; hun naam blijft alleen staan als de stamboom namen van levenden aan deze lezer toont, anders staat er „Privé”. Leden zien dus meer dan bezoekers, maar niets wat alleen aan beheerders wordt getoond. Zo kan een boek worden doorgegeven, ook als een beheerder het maakt. De website bevat altijd alleen wat bezoekers mogen zien. Vereist api4webtrees 1.21 of nieuwer.
