@echo off
setlocal enabledelayedexpansion
pushd "%~dp0"
title NeuralLink Studio - Launcher

set JAVA_INSTALLED=0
set MAVEN_INSTALLED=0
set GPP_INSTALLED=0

echo.
echo ======================================================
echo           NeuralLink Studio - Launcher
echo ======================================================
echo.
echo This will check your project files, verify your system
echo meets the requirements to build and run NeuralLink Studio,
echo install anything missing (with your permission), launch
echo the app, and clean up automatically when you close it.
echo.
echo NOTE: The build/run will open in its own window. This
echo launcher window stays separate and will always finish
echo cleanup automatically once the build/run window closes.
echo.

set /p START_CHOICE=Proceed with setup and launch? (Y/N): 
if /i "!START_CHOICE!"=="Y" goto :START_OK

echo.
echo   Cancelled. Nothing was changed.
echo.
pause
popd
endlocal
exit /b 0

:START_OK
:: Create session log
echo Session: %date% %time% > "%TEMP%\neuralink_session.log"
echo.

:: ============================================================
:: STEP 1 - Project file/folder integrity check
:: ============================================================
echo STEP 1 - Checking Project Files
echo ----------------------------------------

set MISSING=0

if not exist "src\main\java\com\simulink\App.java" echo   MISSING: App.java
if not exist "src\main\java\com\simulink\App.java" set MISSING=1

if not exist "src\main\java\com\simulink\model\Block.java" echo   MISSING: Block.java
if not exist "src\main\java\com\simulink\model\Block.java" set MISSING=1

if not exist "src\main\java\com\simulink\model\BlockFactory.java" echo   MISSING: BlockFactory.java
if not exist "src\main\java\com\simulink\model\BlockFactory.java" set MISSING=1

if not exist "src\main\java\com\simulink\model\Connection.java" echo   MISSING: Connection.java
if not exist "src\main\java\com\simulink\model\Connection.java" set MISSING=1

if not exist "src\main\java\com\simulink\model\RouteGrid.java" echo   MISSING: RouteGrid.java
if not exist "src\main\java\com\simulink\model\RouteGrid.java" set MISSING=1

if not exist "src\main\java\com\simulink\model\ClockBlock.java" echo   MISSING: ClockBlock.java
if not exist "src\main\java\com\simulink\model\ClockBlock.java" set MISSING=1

if not exist "src\main\java\com\simulink\model\ConstantBlock.java" echo   MISSING: ConstantBlock.java
if not exist "src\main\java\com\simulink\model\ConstantBlock.java" set MISSING=1

if not exist "src\main\java\com\simulink\model\CosineBlock.java" echo   MISSING: CosineBlock.java
if not exist "src\main\java\com\simulink\model\CosineBlock.java" set MISSING=1

if not exist "src\main\java\com\simulink\model\DisplayBlock.java" echo   MISSING: DisplayBlock.java
if not exist "src\main\java\com\simulink\model\DisplayBlock.java" set MISSING=1

if not exist "src\main\java\com\simulink\model\GainBlock.java" echo   MISSING: GainBlock.java
if not exist "src\main\java\com\simulink\model\GainBlock.java" set MISSING=1

if not exist "src\main\java\com\simulink\model\IntegratorBlock.java" echo   MISSING: IntegratorBlock.java
if not exist "src\main\java\com\simulink\model\IntegratorBlock.java" set MISSING=1

if not exist "src\main\java\com\simulink\model\ScopeBlock.java" echo   MISSING: ScopeBlock.java
if not exist "src\main\java\com\simulink\model\ScopeBlock.java" set MISSING=1

if not exist "src\main\java\com\simulink\model\SineBlock.java" echo   MISSING: SineBlock.java
if not exist "src\main\java\com\simulink\model\SineBlock.java" set MISSING=1

if not exist "src\main\java\com\simulink\model\SumBlock.java" echo   MISSING: SumBlock.java
if not exist "src\main\java\com\simulink\model\SumBlock.java" set MISSING=1

if not exist "src\main\java\com\simulink\ui\BlockNode.java" echo   MISSING: BlockNode.java
if not exist "src\main\java\com\simulink\ui\BlockNode.java" set MISSING=1

if not exist "src\main\java\com\simulink\commands" echo   MISSING: commands folder
if not exist "src\main\java\com\simulink\commands" set MISSING=1

if not exist "backend\simulation_backend.cpp" echo   MISSING: simulation_backend.cpp
if not exist "backend\simulation_backend.cpp" set MISSING=1

if not exist "backend\build-backend.bat" echo   MISSING: build-backend.bat
if not exist "backend\build-backend.bat" set MISSING=1

if not exist "pom.xml" echo   MISSING: pom.xml
if not exist "pom.xml" set MISSING=1

if not exist "examples\iitm-required-model.json" echo   MISSING: iitm-required-model.json
if not exist "examples\iitm-required-model.json" set MISSING=1

if not exist "src\main\resources\icons\app_icon.png" echo   MISSING: app_icon.png
if not exist "src\main\resources\icons\app_icon.png" set MISSING=1

if not exist "scrollbar.css" echo   MISSING: scrollbar.css
if not exist "scrollbar.css" set MISSING=1

if !MISSING!==1 (
    echo.
    echo ERROR: The project folder is incomplete.
    echo Please re-download and re-extract the project, then run this again.
    echo.
    pause
    del /f /q "%TEMP%\neuralink_session.log" >nul 2>&1
    popd
    endlocal
    exit /b 1
)

echo   All project files are present and laid out correctly.
echo.

:: ============================================================
:: STEP 2 - Check system requirements against project needs
:: ============================================================
echo STEP 2 - Checking System Requirements
echo ----------------------------------------

call :CHECK_TOOLS
echo.

if !JAVA_OK!==1 if !MAVEN_OK!==1 if !GPP_OK!==1 (
    echo   Your system already meets all project requirements.
    echo.
    goto :LAUNCH
)

:: ============================================================
:: STEP 3 - Requirements mismatch: explain and ask permission
:: ============================================================
echo STEP 3 - System Does Not Meet Project Requirements
echo ----------------------------------------
echo   This project requires:
echo     - JDK 17 or newer          ^(you have: !JAVA_VER_DISPLAY!^)
echo     - Maven 3.9 or newer       ^(you have: !MAVEN_VER_DISPLAY!^)
echo     - MinGW-w64 g++ compiler
echo.
echo   Your system does not currently meet these requirements.
echo   This script can install only what is missing, using winget.
echo.

set /p CHOICE=Is it OK to install the missing requirements now? (Y/N): 
if /i "!CHOICE!"=="Y" goto :INSTALL

echo.
echo   Install declined. Nothing changed. Exiting.
echo.
pause
del /f /q "%TEMP%\neuralink_session.log" >nul 2>&1
popd
endlocal
exit /b 1

:: ============================================================
:: STEP 4 - Install only what's missing
:: ============================================================
:INSTALL
echo.
echo STEP 4 - Installing Missing Requirements
echo ----------------------------------------

if !JAVA_OK!==0 (
    echo   Installing JDK 17...
    winget install -e --id Microsoft.OpenJDK.17 --accept-package-agreements --accept-source-agreements
    if !errorlevel!==0 set JAVA_INSTALLED=1
)

if !MAVEN_OK!==0 (
    echo   Installing Maven...
    winget install -e --id Apache.Maven --accept-package-agreements --accept-source-agreements
    if !errorlevel!==0 set MAVEN_INSTALLED=1
)

if !GPP_OK!==0 (
    echo   Installing MinGW-w64...
    winget install -e --id MSYS2.MSYS2 --accept-package-agreements --accept-source-agreements
    if !errorlevel!==0 set GPP_INSTALLED=1
)

echo.
echo   Re-checking your system...
call :CHECK_TOOLS

if !JAVA_OK!==0 echo   WARNING: Java 17+ still not found ^(found: !JAVA_VER_DISPLAY!^).
if !MAVEN_OK!==0 echo   WARNING: Maven 3.9+ still not found ^(found: !MAVEN_VER_DISPLAY!^).
if !GPP_OK!==0 echo   WARNING: g++ still not found.

if !JAVA_OK!==0 goto :MANUAL_INSTRUCT
if !MAVEN_OK!==0 goto :MANUAL_INSTRUCT
if !GPP_OK!==0 goto :MANUAL_INSTRUCT

echo   All requirements installed and verified.
echo.
goto :LAUNCH

:MANUAL_INSTRUCT
echo.
echo   Some tools could not be installed automatically. Please install manually:
echo   JDK:     https://adoptium.net/
echo   Maven:   https://maven.apache.org/download.cgi
echo   MinGW:   https://www.mingw-w64.org/
echo.
pause
del /f /q "%TEMP%\neuralink_session.log" >nul 2>&1
popd
endlocal
exit /b 1

:: ============================================================
:: STEP 5 - Compile and launch
:: ============================================================
:LAUNCH
echo STEP 5 - Building and Launching
echo ----------------------------------------

echo   Compiling C++ backend...
call "backend\build-backend.bat"
if not !errorlevel!==0 (
    echo   ERROR: C++ backend build failed.
    pause
    del /f /q "%TEMP%\neuralink_session.log" >nul 2>&1
    popd
    endlocal
    exit /b 1
)
echo   Backend compiled.
echo.

echo   Building and launching NeuralLink Studio...
echo.
echo   A separate "Build and Run" window will open for the build
echo   log. The actual app window (titled "NeuralLink Studio")
echo   will appear once the build finishes - that's the one to
echo   use, and the one to close when you're done.
echo.
echo   This launcher window is now protected: even if the build
echo   window or the app is closed/force-killed, THIS window
echo   will still finish cleanup automatically. Do not close
echo   THIS window manually.
echo.

start "NeuralLink Studio - Build and Run Log" /wait cmd /c "mvn clean compile javafx:run"

echo.
echo   Build/run window closed. Continuing with cleanup...
echo.

:: ============================================================
:: STEP 6 - Full automatic cleanup
:: ============================================================
echo STEP 6 - Cleaning Up
echo ----------------------------------------

if !JAVA_INSTALLED!==1 (
    echo   Revoking JDK 17 ^(installed this session^)...
    winget uninstall -e --id Microsoft.OpenJDK.17 --accept-source-agreements >nul 2>&1
    echo   Done.
)

if !MAVEN_INSTALLED!==1 (
    echo   Revoking Maven ^(installed this session^)...
    winget uninstall -e --id Apache.Maven --accept-source-agreements >nul 2>&1
    echo   Done.
)

if !GPP_INSTALLED!==1 (
    echo   Revoking MSYS2 ^(installed this session^)...
    winget uninstall -e --id MSYS2.MSYS2 --accept-source-agreements >nul 2>&1
    echo   Done.
)

if !JAVA_INSTALLED!==0 if !MAVEN_INSTALLED!==0 if !GPP_INSTALLED!==0 (
    echo   Nothing to revoke ^(nothing was installed this session^).
)

echo   Clearing session log...
del /f /q "%TEMP%\neuralink_session.log" >nul 2>&1

echo   Verifying cleanup...
if exist "%TEMP%\neuralink_session.log" (
    echo   WARNING: session log could not be removed.
) else (
    echo   Session log removed successfully.
)
echo   Cleanup verified - your system has been restored.
echo.

echo ======================================================
echo   Thank you for using NeuralLink Studio!
echo ======================================================
echo.
echo   Closing in 3 seconds...
timeout /t 3 >nul

popd
endlocal
exit /b 0

:: ============================================================
:: Subroutine: CHECK_TOOLS
:: Verifies Java 17+, Maven 3.9+, and MinGW g++ are on PATH,
:: not just present but at the minimum required version.
:: Sets JAVA_OK / MAVEN_OK / GPP_OK to 0 or 1, plus
:: JAVA_VER_DISPLAY / MAVEN_VER_DISPLAY for messages.
:: ============================================================
:CHECK_TOOLS
set JAVA_OK=0
set JAVA_VER_DISPLAY=not found
where java >nul 2>&1
if !errorlevel!==0 (
    set JAVA_RAW=
    for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set JAVA_RAW=%%v
    set JAVA_RAW=!JAVA_RAW:"=!
    set JAVA_VER_DISPLAY=!JAVA_RAW!
    for /f "delims=." %%a in ("!JAVA_RAW!") do set JAVA_MAJOR=%%a
    if "!JAVA_MAJOR!"=="1" set JAVA_MAJOR=8
    if !JAVA_MAJOR! GEQ 17 set JAVA_OK=1
)
if !JAVA_OK!==1 (
    echo   Java: found ^(!JAVA_VER_DISPLAY!, OK^)
) else (
    echo   Java: !JAVA_VER_DISPLAY! ^(need JDK 17 or newer^)
)

set MAVEN_OK=0
set MAVEN_VER_DISPLAY=not found
where mvn >nul 2>&1
if !errorlevel!==0 (
    set MAVEN_RAW=
    for /f "tokens=3" %%v in ('mvn -version 2^>^&1 ^| findstr /i "Apache Maven"') do set MAVEN_RAW=%%v
    set MAVEN_VER_DISPLAY=!MAVEN_RAW!
    for /f "tokens=1,2 delims=." %%a in ("!MAVEN_RAW!") do (
        set MAVEN_MAJOR=%%a
        set MAVEN_MINOR=%%b
    )
    if !MAVEN_MAJOR! GTR 3 set MAVEN_OK=1
    if !MAVEN_MAJOR!==3 if !MAVEN_MINOR! GEQ 9 set MAVEN_OK=1
)
if !MAVEN_OK!==1 (
    echo   Maven: found ^(!MAVEN_VER_DISPLAY!, OK^)
) else (
    echo   Maven: !MAVEN_VER_DISPLAY! ^(need 3.9 or newer^)
)

set GPP_OK=0
where g++ >nul 2>&1
if !errorlevel!==0 set GPP_OK=1
if !GPP_OK!==1 (
    echo   MinGW g++: found
) else (
    echo   MinGW g++: NOT found
)

exit /b 0
