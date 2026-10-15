@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
call build.bat
if errorlevel 1 exit /b 1
if not exist build\weather-tests mkdir build\weather-tests
javac -encoding UTF-8 -cp "server\bin;server\libs\*" -d build\weather-tests tests\WeatherAPIServiceTest.java tests\CityAPIServiceTest.java
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -cp "build\weather-tests;server\bin;server\libs\*" service.WeatherAPIServiceTest
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -cp "build\weather-tests;server\bin;server\libs\*" service.CityAPIServiceTest
if errorlevel 1 exit /b 1
