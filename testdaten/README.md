# Testdaten

`falkenrath-1.3.ged` ist Version 1.3 des Demo-Stammbaums „Familie Falkenrath“, eingefroren für die Tests
(Plausibilitätsprüfung, GEDCOM-Import). Der Baum selbst mit Bildern und allen Versionen liegt in
[thobgg/falkenrath-demo-tree](https://github.com/thobgg/falkenrath-demo-tree) (CC0).

`falkenrath-1.7-fehler.ged` (GEDCOM 5.5.1) und `falkenrath-1.7-fehler-gedcom7.ged` (GEDCOM 7.0) sind Version 1.7 mit
einem angehängten Block von Fehlerfällen, je Prüfregel einer. `fehlerfaelle.tsv` sagt, welche Kennung welche Regel
auslösen muss. Erzeugt mit `werkzeuge/fehlerdatei.py` im Repo des Demo-Stammbaums; geprüft in `FehlerdateienTest`.
