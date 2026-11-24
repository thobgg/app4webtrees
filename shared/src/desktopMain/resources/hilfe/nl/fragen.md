# Vragen, fouten, privacy

## Vragen en fouten melden

![](desktop-hilfe.jpg)


wtWin, wtTux, wtMac en wtAnd zijn geen officiële webtrees-producten. Stel uw vragen alstublieft niet op het webtrees-forum, maar open een issue op **github.com/thobgg/app4webtrees/issues**. Handig: de versie uit **Help › Over wtWin** (met buildnummer), Windows of Linux, en wat u aan het doen was toen het gebeurde. Bij de stamboom op deze pc ook het bestand `php.log` (zie [Stamboom op deze pc](hilfe:lokal)).

Nieuwe versies verschijnen op **github.com/thobgg/app4webtrees/releases**. Het Windows-bestand is niet ondertekend, dus Windows waarschuwt bij de eerste start voor een onbekende uitgever („Meer informatie“, daarna „Toch uitvoeren“).

## Wat het programma opslaat

- Serveradres, gebruikersnaam en de sessiecookie, **nooit het webtrees-wachtwoord**. Alleen de gegevens van een mapbeveiliging (indien ingevuld) blijven op de pc, ze moeten met elk verzoek mee.
- Vanaf api4webtrees 1.30 een apparaattoken om stil opnieuw aan te melden. Het wisselt bij elk gebruik; een buitgemaakt token wordt daardoor waardeloos en blokkeert het apparaat op de server.
- Uw instellingen (indeling, uiterlijk, instellingen voor schema's en lijsten, kleurregels, afvinklijst van de controle) op deze pc.
- Bij de stamboom op deze pc: de hele stamboom in de map `app4webtrees`, inclusief de toegang tot de lokale webtrees.
- Geen tracking, geen reclame, geen doorgifte.

## Privacy in de stamboom

Het programma meldt zich aan met uw webtrees-account; elke aanvraag loopt als deze gebruiker. Dezelfde regels gelden als op de website: levende personen, afgeschermde records en privéstambomen zijn alleen zichtbaar als webtrees dat toestaat. Uitvoer (schema's, lijsten, boeken, PDF) bevat alleen wat u mag zien.

## Onversleuteld in het thuisnetwerk

Een `http://`-adres wordt alleen in het thuisnetwerk geaccepteerd (privéadressen zoals 192.168…, namen zoals `diskstation` of `.local`). De verbinding is dan onversleuteld, wat thuis prima is. Om de stamboom onderweg te bereiken, heeft de server HTTPS nodig; nas4webtrees levert de handleiding.

## Telefoon en tablet

Voor Android is er **wtAnd**, uit dezelfde familie met dezelfde gegevens: stamboom, foto's, bewerken, gedenkdagen. Een QR-code op de webtrees-pagina **App** verbindt de telefoon zonder typen. wtAnd heeft een server nodig; de stamboom op deze pc is alleen op de pc bereikbaar.

## Updatecontrole

Eén keer per dag, bij het starten, vraagt het programma bij `wtwin.bgg-home.de` het huidige versienummer op – een klein tekstbestand, verder niets. Er wordt niets verzonden behalve die aanvraag: geen stamboom, geen kenmerk, geen gebruik. Is er een nieuwere versie, dan staat in de statusbalk „Versie 1.52 is beschikbaar – Downloaden”; klikken opent de downloadpagina, × slaat deze versie over. Zonder netwerk gebeurt er niets. Uitschakelen: **Help › Op updates controleren**; handmatig controleren: **Help › Nu op updates controleren …**. Een update is alleen eroverheen installeren; instellingen, aanmelding en de stamboom op deze pc blijven.

## Licentie

wtWin valt onder de GPL-3, net als webtrees. De stamboom op deze pc bevat webtrees ongewijzigd ten opzichte van de officiële uitgave (webtrees.net).

## Volledige handleiding

Met afbeeldingen en actuele aanwijzingen: **wtwin.bgg-home.de** (knop onderaan dit venster).
