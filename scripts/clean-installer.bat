@echo off
setlocal
cd /d "%~dp0\.."
if exist build\runtime rmdir /s /q build\runtime
if exist build\jpackage rmdir /s /q build\jpackage
if exist dist rmdir /s /q dist
mkdir build 2>nul
mkdir dist 2>nul
exit /b 0
