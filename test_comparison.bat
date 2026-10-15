@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
call build.bat
if errorlevel 1 exit /b 1
if not exist build\comparison-tests mkdir build\comparison-tests
javac -encoding UTF-8 -cp "server\bin;server\libs\*" -d build\comparison-tests tests\CountryAPIServiceTest.java tests\ComparisonServiceTest.java
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -cp "build\comparison-tests;server\bin;server\libs\*" service.CountryAPIServiceTest
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -cp "build\comparison-tests;server\bin;server\libs\*" service.ComparisonServiceTest
if errorlevel 1 exit /b 1
javac -encoding UTF-8 -cp "client\bin;client\libs\*" -d build\comparison-tests tests\ComparisonTableModelTest.java tests\VietnameseTextTest.java tests\ConsoleEncodingTest.java
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "build\comparison-tests;client\bin;client\libs\*" panel.ComparisonTableModelTest
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "build\comparison-tests;client\bin;client\libs\*" VietnameseTextTest
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -cp "build\comparison-tests;client\bin;client\libs\*" util.ConsoleEncodingTest
if errorlevel 1 exit /b 1
