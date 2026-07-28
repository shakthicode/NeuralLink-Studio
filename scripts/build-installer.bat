@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0\.."
title NeuralLink Studio Installer Builder

set "APP_NAME=NeuralLinkStudio"
set "APP_VERSION=1.0.1"
set "MAIN_JAR=NeuralLinkStudio.jar"
set "MAIN_CLASS=com.simulink.Launcher"
set "BUILD_DIR=%CD%\build"
set "INPUT_DIR=%CD%\target\installer-input"
set "RUNTIME_DIR=%BUILD_DIR%\runtime"
set "APP_IMAGE_DIR=%BUILD_DIR%\jpackage"
set "BACKEND_EXE=%CD%\backend\simulation_backend.exe"
set "LOG_DIR=%BUILD_DIR%\logs"

call :require java.exe "JDK 17 or newer" || exit /b 1
call :require javac.exe "Full JDK" || exit /b 1
call :require jlink.exe "jlink" || exit /b 1
call :require jpackage.exe "jpackage" || exit /b 1
call :require mvn.cmd "Apache Maven" || exit /b 1
call :require g++.exe "MinGW-w64 g++" || exit /b 1
call :find_inno || exit /b 1
call :check_java_version || exit /b 1
call :check_assets || exit /b 1

call scripts\clean-installer.bat || exit /b 1
mkdir "%LOG_DIR%" 2>nul

echo [1/7] Compiling static C++ backend...
g++ -std=c++17 -O2 -Wall -Wextra -pedantic backend\simulation_backend.cpp -static -static-libgcc -static-libstdc++ -o "%BACKEND_EXE%" >"%LOG_DIR%\backend-build.log" 2>&1
if errorlevel 1 (
  type "%LOG_DIR%\backend-build.log"
  echo [ERROR] C++ backend compilation failed.
  exit /b 1
)

echo [2/7] Building and testing Java application...
call mvn.cmd -Dskip.backend.build=true clean package >"%LOG_DIR%\maven-build.log" 2>&1
if errorlevel 1 (
  type "%LOG_DIR%\maven-build.log"
  echo [ERROR] Maven build or tests failed.
  exit /b 1
)

if not exist "%INPUT_DIR%" mkdir "%INPUT_DIR%"
mkdir "%INPUT_DIR%\backend" 2>nul
copy /y "%BACKEND_EXE%" "%INPUT_DIR%\backend\simulation_backend.exe" >nul

echo [3/7] Creating trimmed bundled Java runtime...
set "MODULE_PATH=%JAVA_HOME%\jmods"
if not exist "%MODULE_PATH%" (
  for %%J in (jlink.exe) do set "JLINK_PATH=%%~$PATH:J"
  for %%J in ("!JLINK_PATH!\..\..") do set "JAVA_HOME=%%~fJ"
  set "MODULE_PATH=!JAVA_HOME!\jmods"
)
if not exist "%MODULE_PATH%" (
  echo [ERROR] JDK jmods directory was not found. Set JAVA_HOME to a full JDK.
  exit /b 1
)
jlink --module-path "%MODULE_PATH%" --add-modules java.base,java.desktop,java.logging,java.prefs,java.xml,jdk.unsupported --output "%RUNTIME_DIR%" --strip-debug --no-header-files --no-man-pages --compress=2 >"%LOG_DIR%\jlink.log" 2>&1
if errorlevel 1 (
  type "%LOG_DIR%\jlink.log"
  echo [ERROR] jlink runtime creation failed.
  exit /b 1
)

echo [4/7] Creating standalone Windows application image...
mkdir "%APP_IMAGE_DIR%" 2>nul
jpackage --type app-image --name "%APP_NAME%" --app-version "%APP_VERSION%" --vendor "NeuralLink Studio" --description "JavaFX block-diagram simulation studio" --copyright "Copyright (c) 2026 NeuralLink Studio" --input "%INPUT_DIR%" --main-jar "%MAIN_JAR%" --main-class "%MAIN_CLASS%" --runtime-image "%RUNTIME_DIR%" --dest "%APP_IMAGE_DIR%" --icon "src\main\resources\icons\app_icon.ico" --java-options "-Dfile.encoding=UTF-8" --java-options "-Djavafx.preloader=" --java-options "-Xms64m" --java-options "-Xmx512m" >"%LOG_DIR%\jpackage.log" 2>&1
if errorlevel 1 (
  type "%LOG_DIR%\jpackage.log"
  echo [ERROR] jpackage application image creation failed.
  exit /b 1
)

echo [5/7] Validating application image...
call scripts\validate-installer.bat "%APP_IMAGE_DIR%\%APP_NAME%" || exit /b 1

echo [6/7] Smoke-testing native launcher...
call scripts\smoke-test-app.bat "%APP_IMAGE_DIR%\%APP_NAME%" || exit /b 1

echo [7/7] Compiling single-file Inno Setup installer...
"%ISCC%" /Qp installer\NeuralLinkStudio.iss >"%LOG_DIR%\inno-build.log" 2>&1
if errorlevel 1 (
  type "%LOG_DIR%\inno-build.log"
  echo [ERROR] Inno Setup compilation failed.
  exit /b 1
)

if not exist "dist\NeuralLinkStudio-Setup-%APP_VERSION%.exe" (
  echo [ERROR] Installer compiler completed but expected setup executable is missing.
  exit /b 1
)

echo.
echo ============================================================
echo SUCCESS
echo Installer: dist\NeuralLinkStudio-Setup-%APP_VERSION%.exe
echo Logs:      build\logs
echo ============================================================
exit /b 0

:require
where %~1 >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Missing %~2 ^(%~1^). Add it to PATH and retry.
  exit /b 1
)
exit /b 0

:find_inno
set "ISCC="
for /f "delims=" %%I in ('where ISCC.exe 2^>nul') do if not defined ISCC set "ISCC=%%I"
if not defined ISCC if exist "%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe" set "ISCC=%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe"
if not defined ISCC if exist "%ProgramFiles%\Inno Setup 6\ISCC.exe" set "ISCC=%ProgramFiles%\Inno Setup 6\ISCC.exe"
if not defined ISCC if exist "%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe" set "ISCC=%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe"
if not defined ISCC (
  echo [ERROR] Inno Setup 6 compiler ISCC.exe was not found.
  echo Checked PATH, Program Files and the current-user installation folder.
  exit /b 1
)
echo [OK] Inno Setup: %ISCC%
exit /b 0

:check_java_version
set "JAVA_VERSION="
set "JAVA_MAJOR="
set "JAVA_MAJOR_INVALID="
for /f "tokens=2" %%V in ('javac -version 2^>^&1') do set "JAVA_VERSION=%%V"
if not defined JAVA_VERSION (
  echo [ERROR] Could not determine the JDK version from javac.
  exit /b 1
)
for /f "tokens=1 delims=." %%M in ("%JAVA_VERSION%") do set "JAVA_MAJOR=%%M"
if "%JAVA_MAJOR%"=="1" for /f "tokens=2 delims=." %%M in ("%JAVA_VERSION%") do set "JAVA_MAJOR=%%M"
for /f "delims=0123456789" %%N in ("%JAVA_MAJOR%") do set "JAVA_MAJOR_INVALID=%%N"
if defined JAVA_MAJOR_INVALID (
  echo [ERROR] Invalid JDK version detected: %JAVA_VERSION%
  exit /b 1
)
if %JAVA_MAJOR% LSS 17 (
  echo [ERROR] JDK 17 or newer is required. Detected: %JAVA_VERSION%
  exit /b 1
)
echo [OK] Java version: %JAVA_VERSION%
exit /b 0

:check_assets
set "ICON_ICO=%CD%\src\main\resources\icons\app_icon.ico"
set "ICON_PNG=%CD%\src\main\resources\icons\app_icon_1024.png"
set "SPLASH_GLOW=%CD%\src\main\resources\splash\brain_glow.png"
set "SPLASH_BRAIN=%CD%\src\main\resources\splash\brain_transparent.png"
if not exist "%ICON_ICO%" (
  echo [ERROR] Installer icon is missing: "%ICON_ICO%"
  exit /b 1
)
if not exist "%ICON_PNG%" (
  echo [ERROR] Application icon is missing: "%ICON_PNG%"
  exit /b 1
)
if not exist "%SPLASH_GLOW%" (
  echo [ERROR] Splash asset is missing: "%SPLASH_GLOW%"
  exit /b 1
)
if not exist "%SPLASH_BRAIN%" (
  echo [ERROR] Splash asset is missing: "%SPLASH_BRAIN%"
  exit /b 1
)
echo [OK] Installer, application icon and splash assets are present.
exit /b 0

