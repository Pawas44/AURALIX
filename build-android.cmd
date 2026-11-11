@echo off
setlocal enabledelayedexpansion
title Building VideoGuard Android APK...

echo ===================================================
echo       VideoGuard - Android APK Build Script
echo ===================================================
echo.

:: Detect root directory
set "ROOT_DIR=%~dp0"
cd /d "%ROOT_DIR%"

:: Set Java Home
if exist "C:\PROGRA~2\Android\openjdk\JDK-17~1.14" (
    set "JAVA_HOME=C:\PROGRA~2\Android\openjdk\JDK-17~1.14"
) else if exist "C:\Program Files (x86)\Android\openjdk\jdk-17.0.14" (
    set "JAVA_HOME=C:\PROGRA~2\Android\openjdk\JDK-17~1.14"
)

:: Set Android SDK root
if not defined ANDROID_HOME (
    if exist "%LOCALAPPDATA%\Android\Sdk" (
        set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
    )
)

echo [*] JAVA_HOME   : %JAVA_HOME%
echo [*] ANDROID_HOME: %ANDROID_HOME%
echo.

:: Ensure local.properties exists
if not exist "%ROOT_DIR%android\local.properties" (
    echo sdk.dir=%LOCALAPPDATA:\=\\%\\Android\\Sdk> "%ROOT_DIR%android\local.properties"
)

echo [*] Running Gradle assembleDebug...
echo.
cd /d "%ROOT_DIR%android"

call gradlew.bat assembleDebug

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ===================================================
    echo  [SUCCESS] APK build ho gaya hai!
    echo ===================================================
    if exist "%ROOT_DIR%android\app\build\outputs\apk\debug\app-debug.apk" (
        copy /y "%ROOT_DIR%android\app\build\outputs\apk\debug\app-debug.apk" "%ROOT_DIR%VideoGuard-debug.apk" >nul
        echo [*] Latest APK copied to:
        echo     %ROOT_DIR%VideoGuard-debug.apk
        echo.
        echo Opening file in File Explorer...
        explorer.exe /select,"%ROOT_DIR%VideoGuard-debug.apk"
    )
) else (
    echo.
    echo ===================================================
    echo  [ERROR] Build fail ho gaya. Upar errors check karein.
    echo ===================================================
)

cd /d "%ROOT_DIR%"
echo.
pause

