@echo off
setlocal
cd /d "%~dp0"
call build.bat
if errorlevel 1 exit /b 1
if not exist build\trip-tests mkdir build\trip-tests
javac -encoding UTF-8 -cp "server\bin;server\libs\*" -d build\trip-tests tests\TripPlannerServiceTest.java
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -cp "build\trip-tests;server\bin;server\libs\*" TripPlannerServiceTest
if errorlevel 1 exit /b 1
