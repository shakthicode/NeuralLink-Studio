@echo off
setlocal
echo Refreshing Windows Explorer icon cache...
taskkill /F /IM explorer.exe >nul 2>&1
timeout /t 2 /nobreak >nul
del /A /Q "%localappdata%\IconCache.db" >nul 2>&1
del /A /F /Q "%localappdata%\Microsoft\Windows\Explorer\iconcache*" >nul 2>&1
start explorer.exe
echo Done. The NeuralLink Studio shortcut should now show the new icon.
pause
