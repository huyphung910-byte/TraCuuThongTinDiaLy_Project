@echo off
chcp 65001 >nul
setlocal

cd /d "%~dp0"
call build.bat
if errorlevel 1 exit /b 1

cd /d "%~dp0client"

set "geo_server_host=%~1"

if defined geo_server_host (
    java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 "-Dserver.host=%geo_server_host%" -cp "bin;libs\*" ClientMain
) else (
    java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -cp "bin;libs\*" ClientMain
)
