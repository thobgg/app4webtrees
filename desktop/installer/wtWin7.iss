; wtWin7 - eigener Installer der Variante fuer webtrees7 (GEDCOM 7.0). Installiert und laeuft neben wtWin:
; eigene AppId (NIE aendern), eigener Ordner, eigene Verknuepfungen, kein Schema wtwin://, entfernt keine andere Fassung.
; Aufruf durch Gradle (:desktop:packageInno mit wtand.variante=7): ISCC.exe /DVersion=x.y.z /DQuelle=<app-image> /DZiel=<Ausgabe> wtWin7.iss

#ifndef Version
  #error "Version fehlt (/DVersion=x.y.z)"
#endif
#ifndef Quelle
  #error "Quelle fehlt (/DQuelle=<Ordner mit wtWin7.exe, app, runtime>)"
#endif
#ifndef Ziel
  #define Ziel "."
#endif

[Setup]
; NIE aendern: an dieser Kennung erkennt der Installer eine vorhandene Installation und ersetzt sie.
AppId={{010FDAB1-296E-4C12-8D89-A312C59A6A7F}
AppName=wtWin7
AppVersion={#Version}
AppVerName=wtWin7 {#Version} (webtrees7 / GEDCOM 7.0)
AppPublisher=bgg-home.de
AppPublisherURL=https://wtwin.bgg-home.de/
AppSupportURL=https://github.com/thobgg/app4webtrees/issues
AppUpdatesURL=https://github.com/thobgg/app4webtrees/releases/latest
VersionInfoVersion={#Version}
VersionInfoProductName=wtWin7
VersionInfoProductVersion={#Version}
VersionInfoCompany=bgg-home.de
VersionInfoDescription=wtWin7 - client for webtrees7 / GEDCOM 7.0 (app4webtrees)
; Signatur (SignPath Foundation) setzt der GitHub-Workflow nach dem Bau auf die fertige exe; Inno Setup selbst signiert nichts.
; Pro Benutzer, ohne Adminrechte - wie der bisherige MSI. {autopf} ist dann %LOCALAPPDATA%\Programs.
PrivilegesRequired=lowest
DefaultDirName={autopf}\wtWin7
DefaultGroupName=wtWin7
DisableProgramGroupPage=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
OutputDir={#Ziel}
OutputBaseFilename=wtWin7-{#Version}
SetupIconFile=..\icons\app.ico
UninstallDisplayIcon={app}\wtWin7.exe
UninstallDisplayName=wtWin7 (webtrees7 / GEDCOM 7.0)
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
; Sprachwahl immer zeigen (vorbelegt mit der Windows-Sprache), beim Update die zuletzt gewaehlte nehmen.
ShowLanguageDialog=yes
LanguageDetectionMethod=uilanguage
UsePreviousLanguage=yes
; Laeuft wtWin7 noch, bittet der Installer, es zu schliessen (Dateien in Benutzung).
CloseApplications=yes
RestartApplications=no
ChangesAssociations=yes

[Languages]
Name: "en"; MessagesFile: "compiler:Default.isl"
Name: "de"; MessagesFile: "compiler:Languages\German.isl"
Name: "fr"; MessagesFile: "compiler:Languages\French.isl"
Name: "nl"; MessagesFile: "compiler:Languages\Dutch.isl"
Name: "es"; MessagesFile: "compiler:Languages\Spanish.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

; Beim Update erst die alten Programmdateien weg: Jar-Namen tragen Versionsnummern, sonst sammeln sich Reste an.
[InstallDelete]
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"

[Files]
Source: "{#Quelle}\*"; DestDir: "{app}"; Flags: recursesubdirs ignoreversion

[Icons]
Name: "{autoprograms}\wtWin7"; Filename: "{app}\wtWin7.exe"
Name: "{autodesktop}\wtWin7"; Filename: "{app}\wtWin7.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\wtWin7.exe"; Description: "{cm:LaunchProgram,wtWin7}"; Flags: nowait postinstall skipifsilent
