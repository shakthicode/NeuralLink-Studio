@echo off
setlocal
set "LOG=%LOCALAPPDATA%\NeuralLink Studio\logs\startup-error.log"
if not exist "%LOG%" (
  echo No startup error log was found at:
  echo %LOG%
  echo.
  echo Try launching NeuralLink Studio once, then run this file again.
  pause
  exit /b 1
)
notepad "%LOG%"
