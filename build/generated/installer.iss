;----------------------------------------------------------------------------------------
; Steganography utility to hide messages into cover files
; Copyright (c) Samir Vaidya (mailto:syvaidya@gmail.com)
;----------------------------------------------------------------------------------------

[Setup]
AppName=OpenStego
AppVersion=0.8.4
AppVerName=OpenStego v0.8.4
AppPublisher=Samir Vaidya
AppPublisherURL=https://www.openstego.com
WizardStyle=modern
SourceDir=C:\Users\wh7rb7\Desktop\StegoRegistry\build\distributions\package
OutputDir=C:\Users\wh7rb7\Desktop\StegoRegistry\build\distributions
DefaultDirName={autopf}\OpenStego
DefaultGroupName=OpenStego
OutputBaseFilename=Setup-OpenStego-0.8.4
LicenseFile=LICENSE
UninstallDisplayIcon={app}\openstego.ico
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog

[Files]
Source: "openstego.bat"; DestDir: "{app}"
Source: "openstego.ico"; DestDir: "{app}"
Source: "README"; DestDir: "{app}"
Source: "LICENSE"; DestDir: "{app}"
Source: "lib\*"; DestDir: "{app}\lib"

[Icons]
Name: "{group}\OpenStego"; Filename: "{app}\openstego.bat"; WorkingDir: "{app}"; IconFilename: "{app}\openstego.ico"
