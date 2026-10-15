@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
call build.bat
if errorlevel 1 exit /b 1
if not exist build\account-tests mkdir build\account-tests
javac -encoding UTF-8 -cp "server\bin;server\libs\*" -d build\account-tests tests\ClientHandlerSessionTest.java tests\SavedLocationsIntegrationTest.java tests\ClientSessionConcurrencyTest.java
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -cp "build\account-tests;server\bin;server\libs\*" ClientHandlerSessionTest
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -cp "build\account-tests;server\bin;server\libs\*" ClientSessionConcurrencyTest
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -cp "build\account-tests;server\bin;server\libs\*" SavedLocationsIntegrationTest
if errorlevel 1 exit /b 1
javac -encoding UTF-8 -cp "client\bin;client\libs\*" -d build\account-tests tests\AccountPanelTest.java tests\ClientSocketManagerTest.java
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "build\account-tests;client\bin;client\libs\*" panel.AccountPanelTest
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "build\account-tests;client\bin;client\libs\*" network.ClientSocketManagerTest
if errorlevel 1 exit /b 1
echo Đã kiểm tra thành công tài khoản và phiên đăng nhập.
