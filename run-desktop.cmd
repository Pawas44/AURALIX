@echo off
echo Starting Auralix Desktop (PC) in Development Mode...
cd desktop
if not exist node_modules (
    echo Installing dependencies...
    call npm install
)
call npm run dev
cd ..
