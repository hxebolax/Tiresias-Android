@echo off
rem Lanza el Gradle del entorno portatil con el JDK, el SDK y la cache de esta carpeta.
rem Uso: herramientas\gradle.cmd <tareas de gradle>   (siempre sin demonio)
setlocal
set "RAIZ=%~dp0.."
for %%I in ("%RAIZ%") do set "RAIZ=%%~fI"
set "JAVA_HOME=%RAIZ%\entorno\jdk"
set "ANDROID_HOME=%RAIZ%\entorno\android-sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
set "GRADLE_USER_HOME=%RAIZ%\entorno\gradle-home"
set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%"
call "%RAIZ%\entorno\gradle\bin\gradle.bat" --no-daemon -p "%RAIZ%" %*
exit /b %errorlevel%
