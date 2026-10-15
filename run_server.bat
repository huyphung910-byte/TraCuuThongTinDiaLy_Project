@echo off
chcp 65001 >nul
setlocal

cd /d "%~dp0"
call build.bat
if errorlevel 1 exit /b 1

cd /d "%~dp0server"

java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -cp "bin;libs\*" ServerMain
