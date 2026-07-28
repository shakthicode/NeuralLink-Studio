NeuralLink Studio — Standalone Windows Build
=============================================

Prefer not to build anything yourself? Download the ready-made installer from
https://neurallink-studio-installer.netlify.app/ instead and skip straight to
the END USERS section below.

END USERS
---------
Run NeuralLinkStudio-Setup-1.0.1.exe, follow the setup wizard, and launch from
the Start Menu or optional desktop shortcut. Java, Maven and MinGW are bundled
or not needed on the destination computer.

DEVELOPERS BUILDING THE SETUP
------------------------------
Required: 64-bit Windows 10/11, JDK 17+, Maven 3.9+, MinGW-w64 g++, Inno Setup 6.
Run CHECK_REQUIREMENTS.bat, then build-installer.bat.

The complete build log is written under build\logs. The final setup is written
to dist\NeuralLinkStudio-Setup-1.0.1.exe.

The build script performs: tool validation, static C++ compilation, Maven tests,
private runtime creation, jpackage app-image creation, file validation, launcher
smoke test, and Inno Setup compilation.

STARTUP DIAGNOSTICS
-------------------
If the installed application does not open, run SHOW_STARTUP_ERROR.bat.
The packaged launcher records uncaught startup failures at:
%LOCALAPPDATA%\NeuralLink Studio\logs\startup-error.log
