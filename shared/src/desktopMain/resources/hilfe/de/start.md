# Erste Schritte

wtWin (unter Linux wtTux) zeigt und bearbeitet einen webtrees-Stammbaum wie ein klassisches Genealogie-Programm: Menüleiste, Navigator, Personenblatt, Tafeln, Listen und Bücher. Die Daten liegen entweder auf einem webtrees-Server (NAS oder Webhoster) oder direkt auf diesem PC.

![](desktop-start.jpg)


Beim ersten Start stehen beide Wege nebeneinander.

## Mit webtrees verbinden

Voraussetzung: ein webtrees 2.2 mit dem Modul **api4webtrees**. Das Modul installiert, wer den Server betreibt; im Paket nas4webtrees ist es schon dabei.

**Am einfachsten ohne Tippen:** Im Browser bei webtrees anmelden, im Menü **App** auf **Mit wtWin verbinden** klicken. Das Programm übernimmt den Verbinden-Link (aus der Zwischenablage oder direkt vom Browser), fragt einmal nach und öffnet den Stammbaum. Adresse und Passwort musst du nicht eingeben. Der Link gilt zehn Minuten und genau einmal.

**Von Hand:**

1. Die Adresse deiner webtrees-Seite eintragen, so wie sie im Browser oben steht. Den Rest kürzt das Programm selbst. Steht eine passende Adresse in der Zwischenablage, wird sie vorgeschlagen.
2. **Verbinden** klicken.
3. Mit Benutzername (oder E-Mail) und Passwort anmelden. **Ohne Anmeldung ansehen** zeigt nur, was Gäste auf der Website sehen.

> **Verzeichnisschutz:** Zeigt der Browser vor webtrees ein kleines Anmeldefenster des Webservers (.htaccess), klappe auf dem Adressbildschirm „Server verlangt vor webtrees Benutzername und Passwort“ auf und trage diese Zugangsdaten dort ein. Das Programm schickt sie mit jeder Anfrage an diesen Server mit; angemeldet wird danach wie gewohnt. Meldet der Server so eine Anmeldung, klappen die Felder von selbst auf.

> **Angemeldet bleiben:** Viele Server räumen die webtrees-Sitzung nach einer Pause von etwa 24 Minuten weg. Ab api4webtrees 1.30 erhält das Programm bei der Anmeldung ein Geräte-Token und meldet sich damit still neu an; das Passwort fragt es erst wieder, wenn das Token 90 Tage nicht benutzt wurde, das Passwort geändert oder das Gerät getrennt wurde. Die verbundenen Geräte stehen in webtrees im Menü **App**, jedes lässt sich dort einzeln trennen. **Abmelden** trennt das Gerät ebenfalls.

Du meldest dich mit deinem normalen webtrees-Konto an. Es gelten dieselben Rechte wie auf der Website: Was du dort nicht siehst, siehst du auch hier nicht; was du dort bearbeiten darfst, darfst du auch hier bearbeiten.

> Unverschlüsselte Adressen (`http://`) nimmt das Programm nur im Heimnetz an, zum Beispiel `http://192.168.178.73:8095`. Für unterwegs braucht der Server HTTPS.

## Stammbaum auf diesem PC

Rechts auf dem Startbildschirm: einen Namen eingeben und **Stammbaum anlegen**, oder **Aus GEDCOM-Datei übernehmen …** für den Umstieg von einem anderen Programm. Danach arbeitest du ohne Server, ohne Passwort und ohne Internet. Mehr dazu im Kapitel [Stammbaum auf diesem PC](hilfe:lokal).

## Stammbaum wechseln, Server wechseln

- Hat der Server mehrere Stammbäume, wechselst du unter **Datei › Stammbaum wechseln**.
- Zu einem anderen Server: **Datei › Abmelden**, dann **Andere Adresse**. Oder auf der webtrees-Seite **App** des anderen Servers auf **Mit wtWin verbinden** klicken; das geht auch bei laufender Sitzung.

## Sprache

Das Programm folgt der Sprache des Systems (Deutsch, Englisch, Französisch, Niederländisch oder Spanisch, sonst Englisch). Unter **Ansicht › Sprache** stellst du eine feste Sprache ein. Beschriftungen, die vom Server kommen (Ereignisnamen, Verwandtschaft), erscheinen in der Sprache des Programms.

## Weiter

- [Das Hauptfenster](hilfe:hauptfenster): Navigator, Symbolleiste, Ansicht
- [Personenblatt und Bearbeiten](hilfe:person)
- [Tafeln](hilfe:tafeln), [Listen](hilfe:listen), [Bücher](hilfe:buecher)
- [Tastenkürzel](hilfe:tasten)
