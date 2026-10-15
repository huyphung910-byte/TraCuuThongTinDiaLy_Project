@echo off
setlocal
cd /d "%~dp0"
if not exist build\thread-pool-tests mkdir build\thread-pool-tests
javac -encoding UTF-8 -d build\thread-pool-tests server\src\server\ClientConnectionPool.java tests\ClientConnectionPoolTest.java
if errorlevel 1 exit /b 1
java -cp build\thread-pool-tests ClientConnectionPoolTest
if errorlevel 1 exit /b 1
