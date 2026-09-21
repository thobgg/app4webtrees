# Offline-Modus: Konzept für einen Lese-Cache

Stand 18.09.2026, nur Konzept – kein Code. Ziel: Die App zeigt ohne Netz, was sie zuletzt gesehen hat. Schreiben
bleibt online-only; eine Warteschlange für Änderungen ist bewusst **nicht** Teil der ersten Stufe.

## Was sich lohnt

| Antwort | Warum | Wie groß |
| - | - | - |
| `Info` | Bäume, Rechte, API-Stufe, Upload-Limit – ohne sie kommt die App nicht bis zum Hauptbildschirm | wenige KB |
| `Individual` (Profil) | zuletzt angesehene Personen samt Ereignissen und Familien | 5–30 KB je Person |
| `Pedigree` + `Descendants` + Geschwister-Antworten | der Baum um die zuletzt gewählten Mittelpersonen | 10–60 KB je Mittelperson |
| `Individuals` ohne Suchwort | die ersten Seiten der Personenliste, damit die Suche etwas anzeigt | 10 KB je Seite |
| `Anniversaries` | Startseite und tägliche Erinnerung | wenige KB |
| Vorschaubilder | kommen schon heute aus dem Plattencache von Coil (Bild-URLs sind signierte, stabile Routen) | Coil-Standard 2 % des freien Speichers |

Nicht sinnvoll: `MediaList` (groß, ändert sich), `Pending` (Moderation ist ein Online-Vorgang), `Tags` (klein, aber nur
zum Schreiben nötig), das CSRF-Token (gehört zur Sitzung, nie speichern).

## Wo die Daten liegen

**Empfehlung: Dateien, kein Room.** Die Antworten sind schon JSON und werden mit kotlinx.serialization gelesen; sie so
abzulegen, wie sie kommen, braucht keine neue Abhängigkeit, kein Schema und keine Migrationen. Room lohnt sich erst,
wenn die App offline *suchen* soll (Namen über alle Seiten) – das ist eine spätere Stufe.

- Ordner `cacheDir/api/<Server-Hash>/<Baum>/<Aktion>/<Parameter-Hash>.json`, daneben `.meta` mit Zeitstempel,
  Benutzername und Modulversion. Der Server-Hash trennt zwei Installationen sauber, der Benutzername verhindert, dass
  nach einem Kontowechsel Daten des vorigen Kontos erscheinen (andere Rechte, andere Sichtbarkeit).
- `cacheDir`, nicht `filesDir`: Android darf den Cache bei Platznot leeren, das ist für einen Cache richtig. Eigene
  Obergrenze zusätzlich (z. B. 50 MB, älteste zuerst weg).
- Datenschutz: Die Dateien enthalten Daten lebender Personen. App-privater Speicher genügt (kein Backup, siehe
  `allowBackup=false`), aber: bei Abmelden und Serverwechsel **alles löschen**, wie heute schon den Cookie.

## Wie Veralten erkannt wird

Das Modul liefert bisher weder ETag noch Änderungszeitpunkt (das steht im API-Vertrag von api4webtrees, der hier
nicht angefasst wird). Also:

1. **Zeigen, dann prüfen:** Ist ein Eintrag vorhanden, wird er sofort angezeigt und im Hintergrund neu geladen;
   kommt die Antwort, ersetzt sie den Eintrag und die Ansicht. Kommt ein Netzfehler, bleibt der Eintrag stehen und die
   App wechselt in den Offline-Zustand (`UiState.offline = true`, Hinweisleiste „Offline – Stand vom …").
2. **Alter je Antwort:** `Info` und `Anniversaries` gelten 1 Tag, Personen und Bäume 30 Tage. Älteres wird offline noch
   gezeigt, aber als „veraltet" beschriftet; online wird es ohnehin ersetzt.
3. **Eigene Schreibzugriffe** machen die betroffenen Einträge ungültig: nach jedem erfolgreichen POST die Person, ihre
   Familien und die Baumansichten des Baums löschen (heute setzt die App dafür `pedigree = null`; derselbe Ort).
4. Später, in einer neuen API-Stufe (Thema für den API-Chat): ein Änderungszähler je Baum in `Info` – dann kann die
   App den ganzen Baum-Cache mit einem Vergleich behalten oder verwerfen.

## Schreiben ohne Netz

Ablehnen, nicht sammeln. Vor jedem POST prüft die App kurz die Verbindung (`ConnectivityManager`); fehlt sie, kommt
sofort „Keine Verbindung – Änderungen brauchen den Server" statt eines Zeitüberlaufs. Die Knöpfe bleiben sichtbar,
damit niemand rätselt, ob er Rechte verloren hat.

Warum keine Warteschlange: Moderation und Rechte entscheidet der Server; ein Einmal-Code oder eine abgelaufene Sitzung
lässt aufgestaute Änderungen scheitern; zwei Geräte könnten dieselbe Person widersprüchlich ändern; und ein Foto von
mehreren MB im Ausgang ist eine eigene Baustelle. Das alles ist lösbar, aber nicht in 1.2.

## Umsetzung in Schritten

1. `WtClient.get()` bekommt eine Cache-Schicht: Schlüssel aus Aktion + Baum + Parametern, Lesen aus Datei, Schreiben
   nach erfolgreicher Antwort. Ein Flag pro Aufruf, welche Aktionen überhaupt gecacht werden.
2. `UiState.offline` und die Hinweisleiste; `fail()` setzt das Flag bei `IOException`, jede erfolgreiche Antwort
   löscht es.
3. Aufräumen: Größenlimit, Löschen bei Abmelden/Serverwechsel, Ungültigmachen nach Schreibzugriffen.
4. Tests: Cache-Schicht mit einem Fake-Client rein auf der JVM (Treffer, Fehltreffer, Veralten, Löschen).

Offen für den API-Chat: Änderungszähler je Baum in `Info`; Bestätigung, dass die signierten Bild-Routen über Tage
stabil bleiben (sonst greift der Coil-Cache offline ins Leere).
