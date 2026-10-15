@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
if not exist build\security-client mkdir build\security-client
if not exist build\security-server mkdir build\security-server
javac -encoding UTF-8 -d build\security-client client\src\security\SecureChannel.java tests\SecureChannelTest.java
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -cp build\security-client SecureChannelTest
if errorlevel 1 exit /b 1
javac -encoding UTF-8 -d build\security-server server\src\security\SecureChannel.java tests\SecureChannelTest.java
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -cp build\security-server SecureChannelTest
if errorlevel 1 exit /b 1
