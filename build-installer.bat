@echo off
setlocal
cd /d "%~dp0"
call "%~dp0scripts\build-installer.bat"
set "BUILD_EXIT=%ERRORLEVEL%"
echo.
if not "%BUILD_EXIT%"=="0" (
  echo Installer build failed. Review the messages above.
  echo.
  pause
  exit /b %BUILD_EXIT%
)
echo Installer build completed successfully.
pause
exit /b 0
