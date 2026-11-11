@echo off
setlocal enabledelayedexpansion
title VideoGuard - iOS Build via GitHub Actions

echo ===================================================
echo       VideoGuard - iOS IPA Build Script
echo ===================================================
echo.
echo [NOTE] iOS apps require Apple Xcode (macOS).
echo Windows cannot compile iOS apps directly.
echo.
echo This repository has a GitHub Actions workflow configured
echo with a macOS runner that automatically builds the iOS IPA!
echo.
echo ===================================================
echo 1. Commit and push current changes to GitHub
echo 2. Open GitHub Actions page to download the IPA
echo ===================================================
echo.

set "ROOT_DIR=%~dp0"
cd /d "%ROOT_DIR%"

set /p PUSH_CHOICE="Do you want to commit and push changes now? (Y/N, default Y): "
if /i "%PUSH_CHOICE%"=="" set PUSH_CHOICE=Y
if /i "%PUSH_CHOICE%"=="Y" (
    echo.
    echo [*] Checking git status...
    git status -s
    echo.
    set /p COMMIT_MSG="Enter commit message (or press Enter for 'Update code'): "
    if "!COMMIT_MSG!"=="" set "COMMIT_MSG=Update code"
    echo.
    echo [*] Adding files to git...
    git add .
    git commit -m "!COMMIT_MSG!"
    echo [*] Pushing to GitHub (origin main)...
    git push origin main
    if %ERRORLEVEL% EQU 0 (
        echo.
        echo [SUCCESS] Code pushed to GitHub!
        echo GitHub Actions (macOS runner) has started building the iOS IPA.
    ) else (
        echo.
        echo [WARNING] Git push encountered an issue. Check your git credentials or network.
    )
)

echo.
echo [*] Opening GitHub Actions in your browser...
start https://github.com/Pawas44/videoguard-ios/actions
echo.
echo Once the macOS workflow finishes, you can download 'VideoGuard-iOS-IPA'
echo directly from the Actions artifact section.
echo.
pause

