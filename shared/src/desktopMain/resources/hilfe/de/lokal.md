# Stammbaum auf diesem PC

Ohne Server legt wtWin den Stammbaum auf diesem Computer an. Im Hintergrund arbeitet ein vollständiges webtrees mit mitgeliefertem PHP, unverändert aus dem offiziellen Release. Es ist nur auf diesem PC erreichbar, startet mit dem Programm und endet mit ihm. Du siehst keinen Server und kein Passwort; das Programm meldet sich selbst an.

## Anlegen

Beim ersten Start rechts: einen Namen eingeben und **Stammbaum anlegen** (leer) oder **Aus GEDCOM-Datei übernehmen …**. Für den Umstieg exportierst du im bisherigen Programm eine GEDCOM-Datei (`.ged`) und wählst sie hier. Übernommen werden Personen, Familien, Ereignisse, Quellen und Notizen. Die GEDCOM-Datei selbst bleibt unverändert; ein zweiter Import legt einen weiteren Stammbaum daneben an und überschreibt nie.

Die Datei darf in UTF-8 (mit oder ohne Byte-Reihenfolge-Marke), UTF-16, ANSEL oder ANSI vorliegen; der Zeichensatz wird wie beim Import in webtrees erkannt und gewandelt. Einzelne unbrauchbare Datensätze (etwa doppelte Kennzeichen) werden übersprungen und in `import.log` aufgeführt, der Rest kommt an.

Fotos kommen aus der GEDCOM nicht mit. Du fügst sie im Programm hinzu oder kopierst sie später in den Medienordner (siehe unten) und verknüpfst sie in webtrees.

## Mehrere Stammbäume

**Datei › Stammbäume auf diesem PC …** zeigt alle Stammbäume mit Personenzahl. Dort kannst du einen öffnen, umbenennen (Titel ändern, Haken oder Enter) oder löschen (nie den letzten), einen weiteren leer anlegen oder eine weitere GEDCOM-Datei übernehmen. Leere Reste früherer, gescheiterter Importe – nur die Beispielperson „John Doe“ von webtrees – räumt das Programm beim Start selbst weg; einen Stammbaum, den du selbst leer angelegt hast, lässt es stehen.

## Wo die Daten liegen

| System | Ordner |
| - | - |
| Windows | `%LOCALAPPDATA%\app4webtrees` (im Explorer oben eintippen) |
| Linux | `~/.local/share/app4webtrees` |

Darin liegt `webtrees/` mit dem Programm und `webtrees/data/` mit der Datenbank (SQLite) und dem Medienordner `media/`. Das Protokoll des PHP-Servers heißt `php.log`. Was wtWin selbst dabei bemerkt (abgebrochene Verbindungen, Wiederholungen, Neustarts des Servers), steht in `wtwin.log` daneben.

## Archiv

Der Stammbaum auf diesem PC bringt das Modul **Sammlungen** mit: Fotos und Dokumente liegen als Ordner unter `data/media`, müssen nicht an Personen hängen und erscheinen im Bereich **Fotos › Archiv** sowie in webtrees im Browser. Kirchenbuchscans aus dem Archiv lassen sich in der Quellenverwaltung als Quelle oder Verweis zuordnen.

## Sicherung

Das Programm sichert nicht automatisch. Zwei Wege:

- Bei geschlossenem wtWin den Ordner `app4webtrees` kopieren, etwa auf einen USB-Stick. Das ist die vollständige Sicherung samt Fotos.
- **webtrees im Browser öffnen**, dort in der Verwaltung den Stammbaum als GEDCOM exportieren. Das sichert die Daten, nicht die Bilder.

## Alles aus webtrees

**Datei › webtrees im Browser öffnen** zeigt dein webtrees im Browser: Verwaltung, Module, Namen ändern, Quellen anlegen. Der Browser verlangt eine eigene Anmeldung. Benutzername ist dein Anmeldename am PC; das Passwort hat das Programm beim Anlegen zufällig erzeugt und in der Datei `zugang.properties` im Ordner `app4webtrees` (siehe oben) abgelegt. Öffne die Datei mit einem Texteditor und kopiere das Passwort. Gib es nicht weiter; es ist der Verwalter-Zugang zu deinem Stammbaum.

## Umzug auf eine NAS oder zu einem Webhoster

Soll die Familie mitlesen oder du an zwei Rechnern arbeiten, zieht der Stammbaum auf einen Server, etwa eine Synology mit nas4webtrees. Danach bedienst du wtWin wie vorher, nur verbunden.

1. In wtWin **webtrees im Browser öffnen**, dort **Verwaltung › Stammbaum › Export** als GEDCOM.
2. Fotos: den Ordner `webtrees/data/media` (siehe oben) nach `data/media` des webtrees auf dem Server kopieren.
3. Auf dem Server einen neuen Stammbaum anlegen und die GEDCOM importieren.
4. In wtWin **Datei › Abmelden**, **Andere Adresse**, die Serveradresse eingeben. Oder auf der Seite **App** des Servers auf **Mit wtWin verbinden** klicken.

Der Stammbaum auf dem PC bleibt dabei erhalten, bis du den Ordner löschst.

## Wenn etwas nicht klappt

Lässt sich der Stammbaum nicht anlegen, zeigt das Programm einen Hinweis mit dem Pfad zu `php.log`. Bitte melde das unter github.com/thobgg/app4webtrees/issues und hänge die Datei an, beim Übernehmen einer GEDCOM-Datei auch `import.log` aus demselben Ordner (Zeichensatz, übersprungene Datensätze, Abbruchgrund). **Hilfe › Über wtWin** zeigt, ob PHP und webtrees gefunden wurden.

**„Verbindung … unterbrochen“:** Bricht die Verbindung zum Stammbaum auf diesem PC ab, obwohl webtrees im Browser geht, filtert meist ein anderes Programm den Netzverkehr von wtWin – Werbe- oder Webschutz, auch der Echtzeitschutz eines Virenscanners. Eine Ausnahme für wtWin (`%LOCALAPPDATA%\Programs\wtWin\wtWin.exe`) in diesem Programm hilft meist. Was wtWin dabei bemerkt, steht in `wtwin.log`.

**Startperson:** Hat ein Stammbaum auf diesem PC noch keine Startperson, fragt das Programm beim Öffnen einmal „Mit wem soll der Stammbaum beginnen?“ – Person suchen und anklicken. Die Wahl gilt als Standardperson des Stammbaums; ändern unter Person › Als Startperson festlegen …
