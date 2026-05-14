@echo off
set MVN=%USERPROFILE%\apache-maven-3.9.6\bin\mvn.cmd
if not exist "%MVN%" (
    echo Maven not found at %MVN%. Please install Maven or update this script.
    exit /b 1
)
"%MVN%" %*
