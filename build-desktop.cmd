@echo off
echo ==============================================
echo    Building Auralix PC (Electron + React)
echo ==============================================

cd desktop

if not exist node_modules (
    echo Installing desktop dependencies...
    call npm install
)

echo Building Electron Main & Renderer...
call npm run build

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ==============================================
    echo    BUILD SUCCESSFUL! Output in desktop/dist
    echo ==============================================
) else (
    echo.
    echo Build failed with error code %ERRORLEVEL%
)

cd ..
