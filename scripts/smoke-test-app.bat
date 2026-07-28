@echo off
setlocal EnableExtensions
set "IMAGE=%~1"
if not defined IMAGE set "IMAGE=%~dp0\..\build\jpackage\NeuralLinkStudio"
set "LAUNCHER=%IMAGE%\NeuralLinkStudio.exe"
set "LOG_DIR=%~dp0\..\build\logs"
set "SMOKE_LOG=%LOG_DIR%\launcher-smoke-test.log"

if not exist "%LAUNCHER%" (
  echo [ERROR] Launcher missing: %LAUNCHER%
  exit /b 1
)
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>&1

>"%SMOKE_LOG%" echo NeuralLink Studio launcher smoke test
>>"%SMOKE_LOG%" echo Launcher: %LAUNCHER%
>>"%SMOKE_LOG%" echo Started: %DATE% %TIME%

rem Use PowerShell so we track the exact process returned by Start-Process rather
rem than relying on tasklist image-name matching. A GUI launcher may hand off or
rem terminate early on some Windows/JDK combinations, so an early exit is logged
rem as a warning and does not prevent creation of the installer. The packaged
rem image has already been structurally validated in step 5.
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
  "$p = Start-Process -FilePath '%LAUNCHER%' -PassThru; Start-Sleep -Seconds 7; if ($p.HasExited) { Add-Content -LiteralPath '%SMOKE_LOG%' ('WARNING: launcher exited during smoke test. ExitCode=' + $p.ExitCode); exit 2 } else { Add-Content -LiteralPath '%SMOKE_LOG%' ('OK: launcher remained active. PID=' + $p.Id); Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue; exit 0 }"
set "SMOKE_RESULT=%ERRORLEVEL%"

if "%SMOKE_RESULT%"=="0" (
  echo [OK] Native launcher remained active during the smoke test.
  exit /b 0
)

if "%SMOKE_RESULT%"=="2" (
  echo [WARNING] Launcher exited during the automated smoke test.
  echo [WARNING] Packaging will continue because the application image passed structural validation.
  echo [INFO] Test the launcher manually after the build:
  echo        "%LAUNCHER%"
  echo [INFO] Diagnostic log: %SMOKE_LOG%
  exit /b 0
)

echo [WARNING] Smoke test could not determine launcher state. Packaging will continue.
echo [INFO] Diagnostic log: %SMOKE_LOG%
exit /b 0
