@echo off
setlocal

rem Local Maven bootstrap for this repo (no global install needed).
rem Uses JDK 21 because this project targets Java 21 in pom.xml.

set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "MAVEN_HOME=%~dp0tools\maven\apache-maven-3.9.6"

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [ERROR] JAVA_HOME not found: "%JAVA_HOME%"
  echo Install JDK 21 or update JAVA_HOME in mvn.cmd
  exit /b 1
)

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  echo [ERROR] Local Maven not found: "%MAVEN_HOME%"
  echo Expected: "%MAVEN_HOME%\bin\mvn.cmd"
  exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"
call "%MAVEN_HOME%\bin\mvn.cmd" %*

endlocal
