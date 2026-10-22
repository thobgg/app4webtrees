<?php
// GEDCOM-Datei in einen Stammbaum einlesen - so, wie es der Import im Browser tut (TreeService::importGedcomFile und
// GedcomLoad), nicht wie der Kommandozeilenbefehl tree-import. Der liest die Datei roh: eine Byte-Reihenfolge-Marke
// oder ein einzelner unbrauchbarer Datensatz brechen alles ab, UTF-16 ergibt einen leeren Baum, ANSEL oder ANSI
// Zeichensalat; und weil auf der Kommandozeile niemand angemeldet ist, landen lebende Personen als "Private" im
// Namensindex und sind nicht zu finden. Hier: als Verwalter angemeldet, Zeichensatz erkannt und gewandelt, Marke
// entfernt, unbrauchbare Datensaetze gemeldet und uebersprungen. Alles mit den webtrees-eigenen Klassen.
//
// Aufruf im webtrees-Ordner (wtWin/wtTux schreibt die Datei dorthin, wo auch router.php liegt, und fuehrt sie dort aus):
//   php gedcom-einlesen.php BENUTZER BAUM DATEI       Datei in den (leeren) Baum einlesen
//   php gedcom-einlesen.php BENUTZER BAUM --loeschen  Baum samt Einstellungen entfernen (nach einem Fehlschlag)
// Ausgabe zeilenweise: ZEICHENSATZ <Name>, HINWEIS <Text>, FORTSCHRITT <Zahl>, FEHLER <Datensatz-Anfang> - <Grund>,
// DATENSAETZE <Zahl>, UEBERSPRUNGEN <Zahl>, GELOESCHT <Baum>, ABBRUCH <Grund> (Rueckgabewert 1).
declare(strict_types=1);

use Fisharebest\Webtrees\Auth;
use Fisharebest\Webtrees\Cli\Console;
use Fisharebest\Webtrees\DB;
// Transaktionen ueber die Verbindung von Illuminate: DB::connection() gibt es ab webtrees 2.3 nicht mehr
use Illuminate\Database\Capsule\Manager;
use Fisharebest\Webtrees\Encodings\CP850;
use Fisharebest\Webtrees\Encodings\UTF16BE;
use Fisharebest\Webtrees\Encodings\UTF16LE;
use Fisharebest\Webtrees\Encodings\UTF8;
use Fisharebest\Webtrees\Encodings\Windows1252;
use Fisharebest\Webtrees\Exceptions\GedcomErrorException;
use Fisharebest\Webtrees\GedcomFilters\GedcomEncodingFilter;
use Fisharebest\Webtrees\Registry;
use Fisharebest\Webtrees\Services\GedcomImportService;
use Fisharebest\Webtrees\Services\TreeService;
use Fisharebest\Webtrees\Services\UserService;
use Fisharebest\Webtrees\Webtrees;

require getcwd() . '/vendor/autoload.php';

Webtrees::new()->bootstrap();
(new Console())->bootstrap();

function abbruch(string $grund): never
{
    echo 'ABBRUCH ', einzeilig($grund), PHP_EOL;
    exit(1);
}

function einzeilig(string $text): string
{
    return mb_strimwidth(trim((string) preg_replace('/\s+/', ' ', strip_tags($text))), 0, 300, '…', 'UTF-8');
}

function ersteZeile(string $text): string
{
    $zeile = strtok(ltrim($text), "\n");
    return mb_strimwidth($zeile === false ? '' : $zeile, 0, 120, '…', 'UTF-8');
}

[, $benutzer_name, $baum_name, $datei] = $argv + [null, '', '', ''];

// Als Verwalter anmelden - wie im Browser. Sonst sieht die Kommandozeile nur oeffentliche Baeume, und der Namensindex
// lebender Personen hiesse "Private".
$user_service = Registry::container()->get(UserService::class);
$benutzer     = $user_service->findByUserName($benutzer_name) ?? $user_service->administrators()->first();
if ($benutzer === null) {
    abbruch('Kein Verwalter-Konto gefunden.');
}
Auth::login($benutzer);

$tree_service = Registry::container()->get(TreeService::class);
$tree         = $tree_service->all()[$baum_name] ?? null;

if ($datei === '--loeschen') {
    if ($tree !== null) {
        $tree_service->delete($tree);
    }
    echo 'GELOESCHT ', $baum_name, PHP_EOL;
    exit(0);
}

if ($tree === null) {
    abbruch('Stammbaum "' . $baum_name . '" nicht gefunden.');
}

$fp = @fopen($datei, 'rb');
if ($fp === false) {
    abbruch('Datei nicht lesbar: ' . $datei);
}

// Zeichensatz aus dem Kopf der Datei (Marke, UTF-16, "1 CHAR …"); unbekannte Angabe: als UTF-8 versuchen statt aufgeben.
$kopf = (string) fread($fp, 1048576);
rewind($fp);
try {
    $kodierung = Registry::encodingFactory()->detect($kopf);
} catch (Throwable $e) {
    echo 'HINWEIS ', einzeilig($e->getMessage()), ' - als UTF-8 gelesen.', PHP_EOL;
    $kodierung = null;
}
$kodierung ??= Registry::encodingFactory()->make(UTF8::NAME);

// UTF-16 wandelt webtrees 2.2.6 selbst fehlerhaft (U+0080 bis U+00FF, also alle Umlaute, werden zu Ersatzzeichen):
// darum hier mit PHP nach UTF-8 umschreiben und die Kopie einlesen.
if ($kodierung::NAME === UTF16LE::NAME || $kodierung::NAME === UTF16BE::NAME) {
    $utf8 = mb_convert_encoding((string) file_get_contents($datei), 'UTF-8', $kodierung::NAME === UTF16LE::NAME ? 'UTF-16LE' : 'UTF-16BE');
    if (str_starts_with($utf8, "\u{FEFF}")) {
        $utf8 = substr($utf8, strlen("\u{FEFF}"));
    }
    fclose($fp);
    $datei .= '.utf8';
    file_put_contents($datei, $utf8);
    unset($utf8);
    $fp        = fopen($datei, 'rb');
    $kodierung = Registry::encodingFactory()->make(UTF8::NAME);
    echo 'HINWEIS UTF-16 nach UTF-8 gewandelt.', PHP_EOL;
}

// "CHAR ANSI" liest webtrees als DOS-Zeichensatz CP850; Windows-Programme meinen damit meist Windows-1252. Die Bytes
// verraten es: Umlaute liegen in CP850 bei 0x80-0x9F, in Windows-1252 bei 0xC0-0xFF.
if ($kodierung::NAME === CP850::NAME) {
    $zaehler = count_chars($kopf, 1);
    $dos = 0;
    $win = 0;
    foreach ($zaehler as $byte => $n) {
        if ($byte >= 0x80 && $byte <= 0x9F) {
            $dos += $n;
        } elseif ($byte >= 0xC0 && $byte <= 0xFF && $byte !== 0xE1) {
            $win += $n;
        }
    }
    if ($win > $dos) {
        $kodierung = Registry::encodingFactory()->make(Windows1252::NAME);
        echo 'HINWEIS Zeichensatz ANSI: nach den Bytes zu urteilen Windows-1252, nicht CP850.', PHP_EOL;
    }
}
echo 'ZEICHENSATZ ', $kodierung::NAME, PHP_EOL;

if (!in_array(GedcomEncodingFilter::class, stream_get_filters(), true)) {
    stream_filter_register(GedcomEncodingFilter::class, GedcomEncodingFilter::class);
}
stream_filter_append($fp, GedcomEncodingFilter::class, STREAM_FILTER_READ, ['src_encoding' => $kodierung::NAME]);

$import = Registry::container()->get(GedcomImportService::class);
$anzahl = 0;
$fehler = 0;

/** Einen Block ganzer Datensaetze einlesen - wie GedcomLoad, nur dass jeder Fehler gemeldet und uebersprungen wird. */
$block_einlesen = static function (string $block) use ($import, $tree, &$anzahl, &$fehler): void {
    $block = str_replace("\r", "\n", $block);
    foreach (preg_split('/\n+(?=0)/', $block) as $satz) {
        if (trim($satz) === '') {
            continue;
        }
        try {
            $import->importRecord($satz, $tree, false);
            $anzahl++;
            if ($anzahl % 500 === 0) {
                echo 'FORTSCHRITT ', $anzahl, PHP_EOL;
            }
        } catch (Throwable $e) {
            $fehler++;
            if ($fehler <= 200) {
                $grund = $e instanceof GedcomErrorException ? 'kein gueltiger GEDCOM-Datensatz' : einzeilig($e->getMessage());
                echo 'FEHLER ', ersteZeile($satz), ' - ', mb_strimwidth($grund, 0, 200, '…', 'UTF-8'), PHP_EOL;
            }
        }
    }
};

try {
    Manager::connection()->beginTransaction();

    $tree->setPreference('keep_media', '0');
    $tree->setPreference('WORD_WRAPPED_NOTES', '0');
    $tree->setPreference('GEDCOM_MEDIA_PATH', '');
    DB::table('gedcom')->where('gedcom_id', '=', $tree->id())->update([
        'gedcom_filename' => basename($datei),
        'imported'        => 0,
    ]);

    // Alte Daten weg (der Baum ist neu, aber sicher ist sicher) - dieselben Tabellen wie tree-import.
    foreach ([
        DB::table('individuals')->where('i_file', '=', $tree->id()),
        DB::table('families')->where('f_file', '=', $tree->id()),
        DB::table('sources')->where('s_file', '=', $tree->id()),
        DB::table('other')->where('o_file', '=', $tree->id()),
        DB::table('places')->where('p_file', '=', $tree->id()),
        DB::table('placelinks')->where('pl_file', '=', $tree->id()),
        DB::table('name')->where('n_file', '=', $tree->id()),
        DB::table('dates')->where('d_file', '=', $tree->id()),
        DB::table('change')->where('gedcom_id', '=', $tree->id()),
        DB::table('link')->where('l_file', '=', $tree->id()),
        DB::table('media_file')->where('m_file', '=', $tree->id()),
        DB::table('media')->where('m_file', '=', $tree->id()),
    ] as $query) {
        $query->delete();
    }

    $rest  = '';
    $erste = true;
    while (!feof($fp)) {
        $rest .= (string) fread($fp, 65536);
        if ($erste && $rest !== '') {
            if (str_starts_with($rest, UTF8::BYTE_ORDER_MARK)) {
                $rest = substr($rest, strlen(UTF8::BYTE_ORDER_MARK));
            }
            $rest = ltrim($rest);
            if (preg_match('/^0[ \t]+HEAD/i', $rest) !== 1) {
                abbruch('Keine GEDCOM-Datei: sie beginnt nicht mit "0 HEAD", sondern mit "' . ersteZeile($rest) . '".');
            }
            $erste = false;
        }
        // Nur ganze Datensaetze verarbeiten: bis zum letzten Zeilenumbruch, auf den eine 0 folgt (wie TreeService).
        $ende = max((int) strrpos($rest, "\r0"), (int) strrpos($rest, "\n0"));
        if ($ende > 0) {
            $block_einlesen(substr($rest, 0, $ende + 1));
            $rest = substr($rest, $ende + 1);
        }
    }
    fclose($fp);
    if ($erste) {
        abbruch('Die Datei ist leer.');
    }
    $block_einlesen($rest);

    DB::table('gedcom')->where('gedcom_id', '=', $tree->id())->update(['imported' => 1]);
    Manager::connection()->commit();
} catch (Throwable $e) {
    Manager::connection()->rollBack();
    abbruch($e->getMessage());
}

if (str_ends_with($datei, '.utf8')) {
    @unlink($datei);
}
echo 'DATENSAETZE ', $anzahl, PHP_EOL;
if ($fehler > 0) {
    echo 'UEBERSPRUNGEN ', $fehler, PHP_EOL;
}
