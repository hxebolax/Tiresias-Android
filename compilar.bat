@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
title Tiresias - Compilar, probar e instalar
cd /d "%~dp0"

set "RAIZ=%~dp0"
set "RAIZ=%RAIZ:~0,-1%"
set "JAVA_HOME=%RAIZ%\entorno\jdk"
set "ANDROID_HOME=%RAIZ%\entorno\android-sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
set "ANDROID_AVD_HOME=%RAIZ%\entorno\avd"
set "GRADLE_USER_HOME=%RAIZ%\entorno\gradle-home"
set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%"
set "GRADLE=%RAIZ%\entorno\gradle\bin\gradle.bat"
set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
set "EMULADOR=%ANDROID_HOME%\emulator\emulator.exe"
set "APK=%RAIZ%\Tiresias.apk"
set "APK_NANO=%RAIZ%\TiresiasNano.apk"
set "APK_PRUEBAS=%RAIZ%\app\build\outputs\apk\debug\app-debug.apk"
set "PAQUETE=com.xebolax.tiresias"
set "PAQUETE_PRUEBAS=com.xebolax.tiresias.pruebas"
set "SERVICIO_PRUEBAS=com.xebolax.tiresias.pruebas/com.xebolax.tiresias.servicio.ServicioTiresias"

rem Uso sin menu: compilar.bat compilar ^| instalar ^| todo ^| emulador ^| antiguo ^| sinplay ^| probar ^| pruebas ^| latencia ^| publicar ^| preparar ^| cerrar
if /i "%~1"=="compilar" goto :compilar_solo
if /i "%~1"=="instalar" goto :instalar_solo
if /i "%~1"=="todo" goto :todo
if /i "%~1"=="emulador" goto :emulador_solo
if /i "%~1"=="antiguo" goto :antiguo_solo
if /i "%~1"=="sinplay" goto :sinplay_solo
if /i "%~1"=="probar" goto :probar_solo
if /i "%~1"=="pruebas" goto :pruebas_solo
if /i "%~1"=="latencia" goto :latencia_solo
if /i "%~1"=="publicar" goto :publicar_solo
if /i "%~1"=="preparar" goto :preparar_solo
if /i "%~1"=="cerrar" goto :cerrar_solo

:menu
echo.
echo ============================================
echo   Tiresias, lector de pantalla para Android
echo ============================================
echo   1. Compilar el APK (y el complemento de Gemini Nano)
echo   2. Compilar e instalar en el movil conectado
echo   3. Instalar el APK ya compilado en el movil
echo   4. Ver moviles conectados
echo   5. Abrir la maquina virtual de pruebas (Android 17)
echo   6. Abrir la maquina virtual antigua (Android 7.0)
echo   7. Abrir la maquina virtual sin Google Play Services
echo   8. Compilar, abrir el emulador, instalar y activar Tiresias en el
echo   9. Pasar las pruebas automaticas (PC y emulador)
echo  10. Medir la latencia en el emulador
echo  11. Compilar y publicar la actualizacion (dist y SynologyDrive\web\Tiresias-Android)
echo  12. Preparar o reparar el entorno (JDK, SDK, NDK, Gradle, emuladores)
echo  13. Cerrar todo lo abierto (emulador, Gradle, adb)
echo   0. Salir
echo.
set "OPCION="
set /p "OPCION=Elige una opcion (0-13): "
if "%OPCION%"=="1" call :compilar & goto :menu
if "%OPCION%"=="2" goto :todo_menu
if "%OPCION%"=="3" call :instalar & goto :menu
if "%OPCION%"=="4" call :dispositivos & goto :menu
if "%OPCION%"=="5" call :emulador tiresias & goto :menu
if "%OPCION%"=="6" call :emulador antiguo & goto :menu
if "%OPCION%"=="7" call :emulador sinplay & goto :menu
if "%OPCION%"=="8" call :probar & goto :menu
if "%OPCION%"=="9" call :pruebas & goto :menu
if "%OPCION%"=="10" call :latencia & goto :menu
if "%OPCION%"=="11" call :publicar & goto :menu
if "%OPCION%"=="12" call :preparar & goto :menu
if "%OPCION%"=="13" call :cerrar & goto :menu
if "%OPCION%"=="0" call :cerrar & exit /b 0
echo Opcion no valida.
goto :menu

:todo_menu
call :compilar
if errorlevel 1 goto :menu
call :instalar
goto :menu

:compilar_solo
call :compilar
exit /b %errorlevel%

:instalar_solo
call :instalar
exit /b %errorlevel%

:emulador_solo
call :emulador tiresias
exit /b %errorlevel%

:antiguo_solo
call :emulador antiguo
exit /b %errorlevel%

:sinplay_solo
call :emulador sinplay
exit /b %errorlevel%

:probar_solo
call :probar
exit /b %errorlevel%

:pruebas_solo
call :pruebas
exit /b %errorlevel%

:latencia_solo
call :latencia
exit /b %errorlevel%

:publicar_solo
call :publicar
exit /b %errorlevel%

:preparar_solo
call :preparar
exit /b %errorlevel%

:cerrar_solo
call :cerrar
exit /b 0

:todo
call :compilar
if errorlevel 1 exit /b 1
call :instalar
exit /b %errorlevel%

rem ---------------------------------------------------------------------------
:entorno
if exist "%JAVA_HOME%\bin\java.exe" if exist "%GRADLE%" if exist "%ANDROID_HOME%\platforms\android-37.0" if exist "%ANDROID_HOME%\ndk\29.0.14206865" exit /b 0
echo.
echo [Entorno] Falta el entorno de compilacion. Se descargara en la carpeta "entorno":
echo           JDK 17, Android SDK con NDK y CMake, Gradle y los emuladores (unos 10 GB).
echo           Al instalar el Android SDK se aceptan sus licencias de Google.
echo           Si tienes el de Rescate Accesible, se copia de ahi lo que sirva.
call :preparar
exit /b %errorlevel%

:preparar
set "ORIGEN="
if exist "%RAIZ%\..\RescateAccesible\entorno\jdk\bin\java.exe" set "ORIGEN=%RAIZ%\..\RescateAccesible\entorno"
if defined ORIGEN (
	powershell -NoProfile -ExecutionPolicy Bypass -File "%RAIZ%\herramientas\preparar_entorno.ps1" -Origen "%ORIGEN%"
) else (
	powershell -NoProfile -ExecutionPolicy Bypass -File "%RAIZ%\herramientas\preparar_entorno.ps1"
)
if errorlevel 1 (
	echo [ERROR] No se pudo preparar el entorno.
	exit /b 1
)
exit /b 0

rem ---------------------------------------------------------------------------
:firma
if exist "%RAIZ%\firma\firma.properties" exit /b 0
echo.
echo [Firma] Creando la firma propia de Tiresias (firma\tiresias.jks)...
echo         Guardala: sin ella no podras actualizar la app sin desinstalarla.
if not exist "%RAIZ%\firma" mkdir "%RAIZ%\firma"
for /f %%c in ('powershell -NoProfile -Command "-join ((48..57)+(65..90)+(97..122) | Get-Random -Count 24 | ForEach-Object {[char]$_})"') do set "CLAVE=%%c"
"%JAVA_HOME%\bin\keytool.exe" -genkeypair -v -keystore "%RAIZ%\firma\tiresias.jks" -alias tiresias -keyalg RSA -keysize 2048 -validity 10000 -storepass !CLAVE! -keypass !CLAVE! -dname "CN=Tiresias, O=XeBoLaX, C=ES" >nul 2>&1
if errorlevel 1 (
	echo [ERROR] No se pudo crear la firma.
	exit /b 1
)
(
	echo archivo=firma/tiresias.jks
	echo alias=tiresias
	echo clave=!CLAVE!
) > "%RAIZ%\firma\firma.properties"
exit /b 0

rem ---------------------------------------------------------------------------
:compilar
call :entorno || exit /b 1
call :firma || exit /b 1
echo.
echo [Compilar] Compilando Tiresias y el complemento de Gemini Nano. La primera vez tarda varios minutos...
call "%GRADLE%" --no-daemon -p "%RAIZ%" :app:assembleRelease :nano:assembleRelease
if errorlevel 1 (
	echo.
	echo [ERROR] La compilacion ha fallado. Revisa los mensajes de arriba.
	exit /b 1
)
rem Un APK por procesador en la carpeta apk; Tiresias.apk es el de casi todos los moviles (arm64)
if not exist "%RAIZ%\apk" mkdir "%RAIZ%\apk"
for %%a in (arm64-v8a armeabi-v7a x86_64 x86) do copy /y "%RAIZ%\app\build\outputs\apk\release\app-%%a-release.apk" "%RAIZ%\apk\Tiresias_%%a.apk" >nul
copy /y "%RAIZ%\apk\Tiresias_arm64-v8a.apk" "%APK%" >nul
copy /y "%RAIZ%\nano\build\outputs\apk\release\nano-release.apk" "%APK_NANO%" >nul
echo.
echo [OK] APK listos: %APK% (arm64, casi todos los moviles)
echo                 %RAIZ%\apk\Tiresias_^<procesador^>.apk (uno por procesador)
echo                 %APK_NANO% (solo para moviles con Gemini Nano)
exit /b 0

rem ---------------------------------------------------------------------------
:pruebas
call :entorno || exit /b 1
echo.
echo [Pruebas] Pruebas en el PC (nucleo: frases, puntuacion, diccionarios, gestos; app: cadenas)...
call "%GRADLE%" --no-daemon -p "%RAIZ%" :nucleo:test :app:testDebugUnitTest
if errorlevel 1 (
	echo [ERROR] Alguna prueba ha fallado. Los informes estan en nucleo\build\reports y app\build\reports.
	exit /b 1
)
echo [OK] Pruebas en el PC superadas.
call :hay_emulador || (
	echo [AVISO] Sin emulador abierto no se pasan las pruebas en el dispositivo ^(opcion 5 abre el emulador^).
	exit /b 0
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%RAIZ%\herramientas\emulador_pruebas.ps1" -Que lector || exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -File "%RAIZ%\herramientas\emulador_pruebas.ps1" -Que accesibilidad || exit /b 1
echo [OK] Todas las pruebas han pasado.
exit /b 0

:latencia
call :entorno || exit /b 1
call :hay_emulador || (
	echo [AVISO] Abre antes el emulador ^(opcion 5^).
	exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%RAIZ%\herramientas\emulador_pruebas.ps1" -Que latencia
exit /b %errorlevel%

:hay_emulador
"%ADB%" start-server >nul 2>&1
"%ADB%" devices | findstr /b "emulator-" >nul
exit /b %errorlevel%

rem ---------------------------------------------------------------------------
:dispositivos
if not exist "%ADB%" call :entorno || exit /b 1
"%ADB%" start-server >nul 2>&1
echo.
"%ADB%" devices -l
exit /b 0

rem ---------------------------------------------------------------------------
rem Abre la maquina virtual %1 (tiresias, antiguo o sinplay) y espera a que arranque.
:emulador
set "AVD=%~1"
if "%AVD%"=="" set "AVD=tiresias"
if not exist "%EMULADOR%" call :preparar || exit /b 1
if not exist "%ANDROID_AVD_HOME%\%AVD%.ini" call :preparar || exit /b 1
"%ADB%" start-server >nul 2>&1
for /f "tokens=1" %%a in ('call "%ADB%" devices ^| findstr /b "emulator-"') do (
	echo [Emulador] Ya hay una maquina virtual abierta ^(%%a^).
	exit /b 0
)
echo.
echo [Emulador] Abriendo la maquina virtual "%AVD%"...
start "Emulador Tiresias" "%EMULADOR%" -avd %AVD% -no-snapshot-save -no-boot-anim
echo [Emulador] Esperando a que arranque (puede tardar uno o dos minutos)...
"%ADB%" wait-for-device
:esperar_arranque
for /f %%b in ('call "%ADB%" -e shell getprop sys.boot_completed 2^>nul') do if "%%b"=="1" goto :arrancado
timeout /t 3 /nobreak >nul
goto :esperar_arranque
:arrancado
echo [OK] Emulador listo.
exit /b 0

rem Compila la variante de pruebas, la instala en el emulador y activa Tiresias en el (NUNCA en un movil).
:probar
call :entorno || exit /b 1
echo.
echo [Compilar] Compilando la variante de pruebas...
call "%GRADLE%" --no-daemon -p "%RAIZ%" :app:assembleDebug
if errorlevel 1 exit /b 1
call :emulador tiresias || exit /b 1
echo.
echo [Instalar] Instalando en el emulador...
"%ADB%" -e install -r "%APK_PRUEBAS%"
if errorlevel 1 (
	echo [ERROR] No se pudo instalar en el emulador.
	exit /b 1
)
"%ADB%" -e shell settings put secure enabled_accessibility_services %SERVICIO_PRUEBAS%
"%ADB%" -e shell settings put secure accessibility_enabled 1
echo [OK] Tiresias activo en el emulador. Para ver lo que dice y cuanto tarda:
echo      "%ADB%" -e logcat -s TiresiasHabla TiresiasLatencia
exit /b 0

rem ---------------------------------------------------------------------------
:instalar
if not exist "%APK%" (
	echo [ERROR] No existe %APK%. Compila primero.
	exit /b 1
)
if not exist "%ADB%" call :entorno || exit /b 1
"%ADB%" start-server >nul 2>&1
set "DISPOSITIVOS=0"
set "SERIE="
for /f "skip=1 tokens=1,2" %%a in ('call "%ADB%" devices') do (
	if "%%b"=="device" (
		echo %%a | findstr /b "emulator-" >nul || (
			set /a DISPOSITIVOS+=1
			set "SERIE=%%a"
		)
	)
	if "%%b"=="unauthorized" echo [AVISO] El movil %%a no ha autorizado la depuracion USB: acepta el aviso en su pantalla.
)
if "%DISPOSITIVOS%"=="0" (
	echo.
	echo [AVISO] No hay ningun movil conectado y autorizado.
	echo         1. Activa las Opciones de desarrollador y la Depuracion USB.
	echo         2. Conecta el movil por USB y acepta el aviso de depuracion.
	echo         Tambien puedes copiar Tiresias.apk al movil e instalarlo a mano.
	exit /b 1
)
if not "%DISPOSITIVOS%"=="1" (
	echo.
	"%ADB%" devices -l
	set /p "SERIE=Hay varios moviles. Escribe el numero de serie del que quieres usar: "
)
rem El APK del procesador de ese movil
set "ABI="
for /f %%p in ('call "%ADB%" -s !SERIE! shell getprop ro.product.cpu.abi') do set "ABI=%%p"
set "APK_MOVIL=%APK%"
if defined ABI if exist "%RAIZ%\apk\Tiresias_!ABI!.apk" set "APK_MOVIL=%RAIZ%\apk\Tiresias_!ABI!.apk"
echo.
echo [Instalar] Instalando Tiresias en !SERIE! (solo se instala: no se cambia ningun ajuste de accesibilidad)...
"%ADB%" -s !SERIE! install -r "!APK_MOVIL!" > "%TEMP%\tiresias_adb.txt" 2>&1
type "%TEMP%\tiresias_adb.txt"
findstr /c:"Success" "%TEMP%\tiresias_adb.txt" >nul
if not errorlevel 1 (
	echo [OK] Tiresias instalado. Activalo en el movil: Ajustes, Accesibilidad, Tiresias.
	exit /b 0
)
findstr /c:"INSTALL_FAILED_UPDATE_INCOMPATIBLE" "%TEMP%\tiresias_adb.txt" >nul
if not errorlevel 1 (
	echo.
	echo [AVISO] Hay otro Tiresias instalado con una firma distinta.
	set "RESPUESTA="
	set /p "RESPUESTA=Desinstalarlo (se borran sus ajustes, etiquetas y descargas) e instalar este? (s/n): "
	if /i "!RESPUESTA!"=="s" (
		"%ADB%" -s !SERIE! uninstall %PAQUETE%
		"%ADB%" -s !SERIE! install "!APK_MOVIL!"
		exit /b !errorlevel!
	)
)
echo [ERROR] No se pudo instalar.
exit /b 1

rem ---------------------------------------------------------------------------
rem Compila y deja en dist el APK con su version y version.json (SHA-256 y
rem novedades de NOVEDADES.txt), y los copia a la carpeta del servidor.
:publicar
call :compilar || exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -File "%RAIZ%\herramientas\publicar_actualizacion.ps1"
exit /b %errorlevel%

rem ---------------------------------------------------------------------------
rem No deja nada en marcha: emulador, demonios de Gradle y Kotlin, y adb.
:cerrar
if exist "%ADB%" (
	for /f "tokens=1" %%a in ('call "%ADB%" devices 2^>nul ^| findstr /b "emulator-"') do "%ADB%" -s %%a emu kill >nul 2>&1
	"%ADB%" kill-server >nul 2>&1
)
if exist "%GRADLE%" call "%GRADLE%" --stop >nul 2>&1
echo [OK] Emulador, Gradle y adb cerrados.
exit /b 0
