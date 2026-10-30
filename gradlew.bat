@echo off
setlocal
set DIR=%~dp0
if defined JAVA_HOME (set JAVA=%JAVA_HOME%in\java.exe) else (set JAVA=java.exe)
"%JAVA%" -jar "%DIR%gradle\wrapper\gradle-wrapper.jar" "%DIR%." %*
