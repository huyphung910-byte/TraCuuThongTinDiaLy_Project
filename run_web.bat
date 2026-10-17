@echo off
chcp 65001 >nul
cd /d "%~dp0"
call build.bat
if errorlevel 1 (pause & exit /b 1)
java -cp "client\bin;client\libs\*" WebClientMain
pause
