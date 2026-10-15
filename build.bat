@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

cd /d "%~dp0"

if not exist server\bin mkdir server\bin
if not exist client\bin mkdir client\bin

(for /r server\src %%F in (*.java) do (
    set "geo_source_path=%%F"
    echo "!geo_source_path:\=/!"
)) > server\sources.txt

javac -encoding UTF-8 -cp "server\libs\*" -d server\bin @server\sources.txt

if errorlevel 1 exit /b 1

(for /r client\src %%F in (*.java) do (
    set "geo_source_path=%%F"
    echo "!geo_source_path:\=/!"
)) > client\sources.txt

javac -encoding UTF-8 -cp "client\libs\*" -d client\bin @client\sources.txt

if errorlevel 1 exit /b 1

echo Đã biên dịch thành công máy khách và máy chủ.
