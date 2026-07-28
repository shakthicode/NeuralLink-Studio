@echo off
setlocal EnableExtensions
cd /d "%~dp0"
title NeuralLink Studio Build Requirements
set "FAILED=0"
call :check java.exe "Java/JDK"
call :check javac.exe "JDK compiler"
call :check jlink.exe "jlink"
call :check jpackage.exe "jpackage"
call :check mvn.cmd "Apache Maven"
call :check g++.exe "MinGW-w64 g++"
set "ISCC="
for /f "delims=" %%I in ('where ISCC.exe 2^>nul') do if not defined ISCC set "ISCC=%%I"
if not defined ISCC if exist "%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe" set "ISCC=%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe"
if not defined ISCC if exist "%ProgramFiles%\Inno Setup 6\ISCC.exe" set "ISCC=%ProgramFiles%\Inno Setup 6\ISCC.exe"
if not defined ISCC if exist "%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe" set "ISCC=%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe"
if defined ISCC (echo [OK] Inno Setup 6: %ISCC%) else (echo [MISSING] Inno Setup 6 compiler ISCC.exe& set "FAILED=1")
echo.
if "%FAILED%"=="0" (echo All installer build requirements are available.) else (echo One or more build requirements are missing.)
pause
exit /b %FAILED%
:check
where %~1 >nul 2>&1
if errorlevel 1 (echo [MISSING] %~2 ^(%~1^)& set "FAILED=1") else (for /f "delims=" %%P in ('where %~1') do echo [OK] %~2: %%P& goto :eof)
exit /b 0
