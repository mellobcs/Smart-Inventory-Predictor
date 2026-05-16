@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-25.0.3
set MAVEN_HOME=C:\Program Files\Maven\apache-maven-3.9.15
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%

cd /d "%~dp0java-backend"

echo =============================================
echo Smart Inventory Predictor - Java Backend
echo =============================================
echo JAVA_HOME=%JAVA_HOME%
"%JAVA_HOME%\bin\java" --version
echo Maven: %MAVEN_HOME%
echo =============================================
echo Building and starting Spring Boot server...
echo.

call "%MAVEN_HOME%\bin\mvn.cmd" clean spring-boot:run -DskipTests

pause