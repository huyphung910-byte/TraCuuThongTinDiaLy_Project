@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
fc /b server\src\util\InputValidation.java client\src\util\InputValidation.java >nul
if errorlevel 1 exit /b 1
fc /b server\src\util\RequestValidation.java client\src\util\RequestValidation.java >nul
if errorlevel 1 exit /b 1
call build.bat
if errorlevel 1 exit /b 1
if not exist build\input-validation-tests mkdir build\input-validation-tests
javac -encoding UTF-8 -cp "server\bin;server\libs\*" -d build\input-validation-tests tests\InputValidationTest.java tests\ClientHandlerInputValidationTest.java tests\ClientHandlerSessionTest.java tests\SavedLocationsIntegrationTest.java
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -cp "build\input-validation-tests;server\bin;server\libs\*" util.InputValidationTest
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -cp "build\input-validation-tests;client\bin;client\libs\*" util.InputValidationTest
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "build\input-validation-tests;server\bin;server\libs\*" ClientHandlerInputValidationTest
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "build\input-validation-tests;server\bin;server\libs\*" ClientHandlerSessionTest
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "build\input-validation-tests;server\bin;server\libs\*" SavedLocationsIntegrationTest
if errorlevel 1 exit /b 1
javac -encoding UTF-8 -cp "client\bin;client\libs\*" -d build\input-validation-tests tests\ClientSocketManagerTest.java tests\AccountPanelTest.java tests\CountrySearchPanelTest.java
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "build\input-validation-tests;client\bin;client\libs\*" network.ClientSocketManagerTest
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "build\input-validation-tests;client\bin;client\libs\*" panel.AccountPanelTest
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "build\input-validation-tests;client\bin;client\libs\*" panel.CountrySearchPanelTest
if errorlevel 1 exit /b 1
echo Đã kiểm tra thành công dữ liệu đầu vào của Client và Server.
