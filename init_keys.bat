@echo off
chcp 65001 >nul
setlocal

cd /d "%~dp0server"

java -Dfile.encoding=UTF-8 -cp "bin;libs\*" security.ServerKeyManager

if errorlevel 1 exit /b 1

if not exist ..\client\security mkdir ..\client\security

copy /y security\server-public.key ..\client\security\server-public.key >nul

if errorlevel 1 exit /b 1

echo Đã sao chép khóa công khai sang máy khách cục bộ.
echo Chỉ giữ khóa riêng trên máy chủ.
