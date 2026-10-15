@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
call build.bat
if errorlevel 1 exit /b 1
if not exist build\country-search-tests mkdir build\country-search-tests
javac -encoding UTF-8 -cp "server\bin;server\libs\*" -d build\country-search-tests tests\CountryAPIServiceTest.java tests\CountryDetailsServiceTest.java
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -cp "build\country-search-tests;server\bin;server\libs\*" service.CountryAPIServiceTest
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "build\country-search-tests;server\bin;server\libs\*" service.CountryDetailsServiceTest
if errorlevel 1 exit /b 1
javac -encoding UTF-8 -cp "client\bin;client\libs\*" -d build\country-search-tests tests\CountrySearchPanelTest.java tests\ClientSocketManagerTest.java
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "build\country-search-tests;client\bin;client\libs\*" panel.CountrySearchPanelTest
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "build\country-search-tests;client\bin;client\libs\*" network.ClientSocketManagerTest
if errorlevel 1 exit /b 1
echo Đã kiểm tra thành công tra cứu quốc gia và ảnh quốc kỳ.
