@echo off
setlocal
cd /d "%~dp0"
if not exist build\map-tests mkdir build\map-tests
javac -encoding UTF-8 -d build\map-tests client\src\panel\CityMapPanel.java tests\CityMapPanelTest.java
if errorlevel 1 exit /b 1
java -Djava.awt.headless=true -cp build\map-tests panel.CityMapPanelTest
if errorlevel 1 exit /b 1
