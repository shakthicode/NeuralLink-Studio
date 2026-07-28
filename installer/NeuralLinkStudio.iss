#define AppName "NeuralLink Studio"
#define AppVersion "1.0.1"
#define AppPublisher "NeuralLink Studio"
#define AppExeName "NeuralLinkStudio.exe"
#define AppId "{{4A5F74AE-7380-4D30-B25F-5B99E38BFB4A}"

[Setup]
AppId={#AppId}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher={#AppPublisher}
VersionInfoVersion={#AppVersion}.0
VersionInfoCompany={#AppPublisher}
VersionInfoDescription={#AppName} standalone Windows installer
VersionInfoProductName={#AppName}
VersionInfoProductVersion={#AppVersion}
DefaultDirName={autopf}\NeuralLink Studio
DefaultGroupName=NeuralLink Studio
DisableProgramGroupPage=yes
OutputDir=..\dist
OutputBaseFilename=NeuralLinkStudio-Setup-{#AppVersion}
SetupIconFile=..\src\main\resources\icons\app_icon.ico
UninstallDisplayIcon={app}\app_icon.ico
WizardImageFile=wizard-large.bmp
WizardSmallImageFile=wizard-small.bmp
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=admin
CloseApplications=yes
RestartApplications=no
SetupLogging=yes
MinVersion=10.0
UsePreviousAppDir=yes
UsePreviousTasks=yes
DisableWelcomePage=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a &desktop shortcut"; GroupDescription: "Additional shortcuts:"; Flags: unchecked

[Files]
Source: "..\src\main\resources\icons\app_icon.ico"; DestDir: "{app}"; DestName: "app_icon.ico"; Flags: ignoreversion
Source: "..\build\jpackage\NeuralLinkStudio\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[InstallDelete]
Type: files; Name: "{autodesktop}\NeuralLink Studio.lnk"
Type: files; Name: "{group}\NeuralLink Studio.lnk"

[Icons]
Name: "{group}\NeuralLink Studio"; Filename: "{app}\{#AppExeName}"; WorkingDir: "{app}"; IconFilename: "{app}\app_icon.ico"
Name: "{autodesktop}\NeuralLink Studio"; Filename: "{app}\{#AppExeName}"; WorkingDir: "{app}"; IconFilename: "{app}\app_icon.ico"; Tasks: desktopicon
Name: "{group}\Uninstall NeuralLink Studio"; Filename: "{uninstallexe}"

[Run]
Filename: "{app}\{#AppExeName}"; Description: "Launch NeuralLink Studio"; Flags: nowait postinstall skipifsilent

[UninstallRun]
Filename: "{cmd}"; Parameters: "/C taskkill /IM {#AppExeName} /T /F >nul 2>&1"; Flags: runhidden; RunOnceId: "StopNeuralLinkStudio"

[Code]
function InitializeSetup(): Boolean;
begin
  Result := True;
  if not IsWin64 then
  begin
    MsgBox('{#AppName} requires 64-bit Windows.', mbError, MB_OK);
    Result := False;
  end;
end;
