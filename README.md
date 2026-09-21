# wtAnd

**Deutsch** · [English](README.en.md)

### [⬇ APK herunterladen](https://github.com/thobgg/wtAnd/releases/latest)

Signiert. Für die Installation außerhalb des Play Store muss Android einmalig erlaubt werden, dass der Browser Apps installiert.
Auf dem webtrees-Server muss das Modul [api4webtrees](https://github.com/thobgg/api4webtrees) installiert sein.

Eine native Android-App für [webtrees](https://webtrees.net/) – den eigenen Stammbaum auf Handy und Tablet
ansehen **und bearbeiten**, mit den eigenen Daten auf dem eigenen Server.

Die App bringt zwei Welten zusammen: die Person, die den Stammbaum am PC akribisch pflegt, und die Familie, die am
Handy oder Tablet hineinschauen und mit Fotos und Hinweisen beitragen will. Die Detailarbeit bleibt am PC. Was aus der
App kommt, landet als ausstehende Änderung in derselben webtrees-Installation, unter deren Rechten, Moderation und Regeln.

| Tablet | Handy |
| - | - |
| ![Baum und Profil nebeneinander](docs/screenshots/tablet-baum.png) | ![Baum am Handy](docs/screenshots/handy-baum.png) |

<sub>Alle Bilder zeigen den frei erfundenen Demo-Stammbaum „Familie Falkenrath" (siehe [demo-tree/](demo-tree/)).</sub>

## Was die App kann

- **Baum als Mittelpunkt:** Sanduhr-Ansicht mit Ahnen, Partnern, Kindern und Enkeln – dazu, wie in einer
  Familienansicht üblich, die Geschwister der Mittelperson und ihrer Ahnen samt Partnern sowie die Cousins
  (beides abschaltbar);
  frei verschieben und zoomen, Zweige nach oben aufklappen, jede Person zur Mittelperson machen
- **Profil:** Lebenslauf als Zeitleiste (mit Heirat und Geburten der Kinder), Verwandtschaft zur eigenen Person
  („Großvater väterlicherseits"), Fotos, Familie, Karte der Lebensstationen (OpenStreetMap)
- **Bearbeiten:** Ereignisse anlegen, ändern, löschen – auch Heirat und andere Familienereignisse; Verwandte direkt im
  Baum über das „+" an jeder Karte anlegen; Verknüpfungen lösen, Personen löschen. Datumsangaben werden ausgewählt
  (genau, um, vor, nach, zwischen · Tag, Monat, Jahr), Orte schlägt die App beim Tippen aus dem Baum vor
- **Fotos:** aufnehmen oder auswählen und einer Person zuordnen – sie werden passend zum Upload-Limit des Servers
  verkleinert; Fotoübersicht des ganzen Baums
- **Jahrestage:** die nächsten Geburts-, Heirats- und Todestage, auf Wunsch mit täglicher Erinnerung
- **Freigabe:** Moderatoren nehmen ausstehende Änderungen direkt in der App an oder verwerfen sie
- **Handy kompakt, Tablet umfassend:** am Tablet stehen Profil und Baum nebeneinander
- Deutsch und Englisch; Geräte in anderen Sprachen sehen Englisch. Die Beschriftungen des Servers kommen in der Sprache der App

Alles, was (noch) nicht nativ geht, öffnet die App als webtrees-Seite in derselben Sitzung.

## Voraussetzung: das webtrees-Modul

webtrees hat keine Schnittstelle für Apps. Die App spricht deshalb mit dem Modul
**[api4webtrees](https://github.com/thobgg/api4webtrees)**, das auf dem eigenen webtrees-Server (2.2.x)
nach `modules_v4/` kopiert wird. Der webtrees-Kern bleibt unverändert.

## Datenschutz

- Die App meldet sich mit dem normalen webtrees-Konto an. Jede Anfrage läuft als dieser Benutzer – es gelten dieselben
  Datenschutzregeln wie auf der Website (lebende Personen, gesperrte Einträge, private Bäume).
- Änderungen landen sofort in webtrees: mit „Änderungen automatisch annehmen" gelten sie gleich, sonst warten sie auf
  die Freigabe durch einen Moderator.
- Gespeichert werden Serveradresse, Benutzername und das Sitzungs-Cookie – **nie das Passwort**. Kein Cloud-Backup der
  App-Daten, keine Analyse, keine Werbung, keine Google-Dienste.
- Berechtigungen: Internet. Für die abschaltbare tägliche Erinnerung an Jahrestage: Benachrichtigungen (wird erst beim
  Einschalten erfragt) sowie die üblichen Rechte des Android-Aufgabenplaners (Netzwerkstatus, Start nach Neustart,
  Wachbleiben, Vordergrunddienst). Für „Foto aufnehmen" ist keine Kamera-Berechtigung nötig – die Kamera-App des Geräts
  macht das Bild. Kein Zugriff auf Kontakte, Standort oder Dateien.

## Weitere Bilder

| Start mit Jahrestagen | Fotoübersicht | Profil am Handy |
| - | - | - |
| ![Start](docs/screenshots/tablet-start.png) | ![Fotos](docs/screenshots/tablet-fotos.png) | ![Profil](docs/screenshots/handy-profil.png) |

## Selbst bauen

```bash
./gradlew :app:assembleDebug
```

`minSdk` 26, `compileSdk` 36, Kotlin und Jetpack Compose. Der Build braucht ein **JDK 21**. Für einen signierten
Release liest der Build `keystore.properties` im Projektwurzelverzeichnis (nicht eingecheckt); fehlt die Datei, wird
mit dem Debug-Schlüssel signiert.

| Ordner | Inhalt |
| - | - |
| `app/` | die App |
| `demo-tree/` | Demo-Stammbaum „Familie Falkenrath" (GEDCOM + Bilder, frei erfunden, CC0) |
| `docs/` | Gestaltungs-Leitfaden und Bildschirmfotos |
| `tools/` | Hilfsskripte: Demo-Baum erzeugen, Server prüfen, UI-Tests per adb |

Einstieg in den Code: `MainActivity` zeigt `ui/MainScreen.kt` (Bildschirmwahl, Navigation, Menü). Der Zustand liegt in
`ui/UiState.kt`, das View-Model `ui/AppViewModel.kt` hält ihn; seine Aktionen stehen nach Bereich in `SessionActions`,
`TreeActions`, `EditActions`, `PhotoActions` und `AnniversaryActions`. Jeder Bereich hat seine Datei (`TreeSection`, `HomeSection`,
`SearchSection`, `PhotosSection`), das Profil besteht aus `ProfilePanel`, `Timeline`, `Relatives` und `LifeMap`.
`api/` spricht mit dem Modul, `ui/tree/` rechnet und zeichnet den Baum, `data/` bereitet Datumsangaben und Fotos auf.

## Lizenz

[GPL-3.0](LICENSE), wie webtrees. Der Demo-Stammbaum in `demo-tree/` steht unter CC0.

Verwandt: [wtAnd (Wrapper)](https://github.com/thobgg/wtAnd-wrapper) – die schlanke WebView-Hülle für alle,
die kein Modul installieren möchten.
