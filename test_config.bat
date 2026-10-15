@echo off
setlocal
cd /d "%~dp0"
fc /b client\src\util\AppConfig.java server\src\util\AppConfig.java >nul
if errorlevel 1 exit /b 1
if not exist build\config-tests mkdir build\config-tests
javac -encoding UTF-8 -d build\config-tests client\src\util\AppConfig.java tests\AppConfigTest.java
if errorlevel 1 exit /b 1
java -cp build\config-tests AppConfigTest
if errorlevel 1 exit /b 1
