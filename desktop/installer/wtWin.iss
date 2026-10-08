; wtWin - Windows-Installer mit Inno Setup (seit 1.42; vorher jpackage-MSI).
; Gradle baut das Programmverzeichnis (:desktop:createDistributable), dieses Skript packt daraus die Setup-exe:
; Sprachwahl beim Start (Deutsch, Englisch, Franzoesisch, Niederlaendisch, Spanisch, vorbelegt mit der Windows-Sprache),
; Installation pro Benutzer ohne Adminrechte, und das Schema wtwin:// fuer "Mit wtWin verbinden" aus dem Browser
; wird gleich eingetragen (das Programm traegt es beim Start zusaetzlich selbst ein, Verbindungslinks.kt).
; Aufruf durch Gradle (:desktop:packageInno): ISCC.exe /DVersion=1.42.315 /DQuelle=<app-image> /DZiel=<Ausgabeordner> wtWin.iss

#ifndef Version
  #error "Version fehlt (/DVersion=x.y.z)"
#endif
#ifndef Quelle
  #error "Quelle fehlt (/DQuelle=<Ordner mit wtWin.exe, app, runtime>)"
#endif
#ifndef Ziel
  #define Ziel "."
#endif

[Setup]
; NIE aendern: an dieser Kennung erkennt der Installer eine vorhandene Installation und ersetzt sie.
AppId={{E6C1F3A0-7B2D-4C7E-9A41-5F0D8B2A6C13}
AppName=wtWin
AppVersion={#Version}
AppVerName=wtWin {#Version}
AppPublisher=bgg-home.de
AppPublisherURL=https://wtwin.bgg-home.de/
AppSupportURL=https://github.com/thobgg/app4webtrees/issues
AppUpdatesURL=https://github.com/thobgg/app4webtrees/releases/latest
VersionInfoVersion={#Version}
VersionInfoDescription=wtWin - client for webtrees (app4webtrees)
; Pro Benutzer, ohne Adminrechte - wie der bisherige MSI. {autopf} ist dann %LOCALAPPDATA%\Programs.
PrivilegesRequired=lowest
DefaultDirName={autopf}\wtWin
DefaultGroupName=wtWin
DisableProgramGroupPage=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
OutputDir={#Ziel}
OutputBaseFilename=wtWin-{#Version}
SetupIconFile=..\icons\app.ico
UninstallDisplayIcon={app}\wtWin.exe
UninstallDisplayName=wtWin
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
; Sprachwahl immer zeigen (vorbelegt mit der Windows-Sprache), beim Update die zuletzt gewaehlte nehmen.
ShowLanguageDialog=yes
LanguageDetectionMethod=uilanguage
UsePreviousLanguage=yes
; Laeuft wtWin noch, bittet der Installer, es zu schliessen (Dateien in Benutzung).
CloseApplications=yes
RestartApplications=no
ChangesAssociations=yes

[Languages]
Name: "en"; MessagesFile: "compiler:Default.isl"
Name: "de"; MessagesFile: "compiler:Languages\German.isl"
Name: "fr"; MessagesFile: "compiler:Languages\French.isl"
Name: "nl"; MessagesFile: "compiler:Languages\Dutch.isl"
Name: "es"; MessagesFile: "compiler:Languages\Spanish.isl"

[CustomMessages]
en.AlteFassung=The previous version of wtWin could not be removed (Windows Installer code %1). Please uninstall wtWin under Settings > Apps and run this setup again.
de.AlteFassung=Die bisherige Fassung von wtWin ließ sich nicht entfernen (Windows-Installer-Code %1). Bitte wtWin unter Einstellungen > Apps deinstallieren und dieses Setup erneut starten.
fr.AlteFassung=La version précédente de wtWin n’a pas pu être supprimée (code Windows Installer %1). Veuillez désinstaller wtWin dans Paramètres > Applications, puis relancer cette installation.
nl.AlteFassung=De vorige versie van wtWin kon niet worden verwijderd (Windows Installer-code %1). Verwijder wtWin via Instellingen > Apps en start deze installatie opnieuw.
es.AlteFassung=No se pudo eliminar la versión anterior de wtWin (código de Windows Installer %1). Desinstale wtWin en Configuración > Aplicaciones y vuelva a ejecutar esta instalación.

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

; Beim Update erst die alten Programmdateien weg: Jar-Namen tragen Versionsnummern, sonst sammeln sich Reste an.
[InstallDelete]
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"

[Files]
Source: "{#Quelle}\*"; DestDir: "{app}"; Flags: recursesubdirs ignoreversion

[Icons]
Name: "{autoprograms}\wtWin"; Filename: "{app}\wtWin.exe"
Name: "{autodesktop}\wtWin"; Filename: "{app}\wtWin.exe"; Tasks: desktopicon

; Verbinden-Link aus webtrees (Seite "App", Knopf "Mit wtWin verbinden"): wtwin://connect?... oeffnet das Programm.
[Registry]
Root: HKA; Subkey: "Software\Classes\wtwin"; ValueType: string; ValueName: ""; ValueData: "URL:wtWin"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\wtwin"; ValueType: string; ValueName: "URL Protocol"; ValueData: ""
Root: HKA; Subkey: "Software\Classes\wtwin\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: """{app}\wtWin.exe"",0"
Root: HKA; Subkey: "Software\Classes\wtwin\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\wtWin.exe"" ""%1"""

[Run]
Filename: "{app}\wtWin.exe"; Description: "{cm:LaunchProgram,wtWin}"; Flags: nowait postinstall skipifsilent

[Code]
// Uebergang von den jpackage-Fassungen (1.21 bis 1.41, Windows Installer, UpgradeCode b3f1d7a2-4c58-4e9b-8f60-1d2e7a9c5b41):
// die alte Installation still entfernen, sonst stuende wtWin zweimal im Startmenue. Einstellungen bleiben, sie liegen
// in der Registry (HKCU\Software\JavaSoft\Prefs), nicht im Programmordner.
function MsiProduktCode(Wurzel: Integer): String;
var
  Namen: TArrayOfString;
  i: Integer;
  Pfad, Name: String;
  WI: Cardinal;
begin
  Result := '';
  if RegGetSubkeyNames(Wurzel, 'Software\Microsoft\Windows\CurrentVersion\Uninstall', Namen) then
    for i := 0 to GetArrayLength(Namen) - 1 do
    begin
      Pfad := 'Software\Microsoft\Windows\CurrentVersion\Uninstall\' + Namen[i];
      if RegQueryStringValue(Wurzel, Pfad, 'DisplayName', Name) and (Name = 'wtWin')
        and RegQueryDWordValue(Wurzel, Pfad, 'WindowsInstaller', WI) and (WI = 1) then
      begin
        Result := Namen[i];
        exit;
      end;
    end;
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  Code: String;
  RC: Integer;
begin
  Result := '';
  Code := MsiProduktCode(HKCU);
  if Code = '' then
    Code := MsiProduktCode(HKLM);
  if Code <> '' then
  begin
    Log('Entferne die Windows-Installer-Fassung ' + Code);
    // 0 = entfernt, 3010 = entfernt, Neustart steht noch aus (laufendes wtWin), 1605 = war schon weg.
    if not Exec('msiexec.exe', '/x ' + Code + ' /qn /norestart', '', SW_HIDE, ewWaitUntilTerminated, RC) then
      RC := -1;
    if (RC <> 0) and (RC <> 3010) and (RC <> 1605) then
      Result := FmtMessage(CustomMessage('AlteFassung'), [IntToStr(RC)]);
  end;
end;
