@echo off
setlocal
if not defined JAVA_HOME (
  echo Set JAVA_HOME to the installed Java 8 JDK/JRE directory.
  exit /b 1
)
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo JAVA_HOME does not contain bin\java.exe.
  exit /b 1
)
if not exist "%~dp0regulus-api.jar" (
  echo Copy build\libs\regulus-api.jar beside this script.
  exit /b 1
)
"%JAVA_HOME%\bin\java.exe" -jar "%~dp0regulus-api.jar" --spring.config.additional-location="optional:file:%~dp0config/" %*
exit /b %ERRORLEVEL%
