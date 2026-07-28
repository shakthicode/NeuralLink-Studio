@echo off
setlocal EnableExtensions
set "IMAGE=%~1"
if not defined IMAGE set "IMAGE=%~dp0\..\build\jpackage\NeuralLinkStudio"
set "FAILED=0"

call :check "%IMAGE%\NeuralLinkStudio.exe"
call :check "%IMAGE%\runtime\bin\java.dll"
call :check "%IMAGE%\app\NeuralLinkStudio.jar"
call :check "%IMAGE%\app\backend\simulation_backend.exe"
call :check "%~dp0\..\src\main\resources\splash\brain_glow.png"
call :check "%~dp0\..\src\main\resources\splash\brain_transparent.png"
call :check "%~dp0\..\src\main\resources\splash\splash-style.css"
call :check "%~dp0\..\src\main\resources\icons\app_icon.ico"
call :check "%~dp0\..\src\main\resources\icons\app_icon_1024.png"

if "%FAILED%"=="1" (
  echo [ERROR] Installer image validation failed.
  exit /b 1
)
echo [OK] Installer image contains launcher, runtime, Java app, backend, splash assets and icons.
exit /b 0

:check
if not exist "%~1" (
  echo [MISSING] %~1
  set "FAILED=1"
) else (
  echo [OK] %~1
)
exit /b 0
