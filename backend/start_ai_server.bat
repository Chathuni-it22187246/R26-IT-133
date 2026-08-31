@echo off
title GreenHands AI Server
cd /d "%~dp0"

echo.
echo  GreenHands AI Server
echo  --------------------
echo  Binding FastAPI to http://0.0.0.0:8002  (reachable on your LAN)
echo  Local:     http://127.0.0.1:8002
echo  Emulator:  http://10.0.2.2:8002
echo.

for /f "usebackq tokens=*" %%i in (`powershell -NoProfile -Command "Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' -and $_.PrefixOrigin -ne 'WellKnown' } | Select-Object -ExpandProperty IPAddress"`) do (
    echo  Phone/LAN: http://%%i:8002
)

echo  Keep this window open while using the Android app.
echo  Phone and PC must be on the same Wi-Fi. Allow Python through Windows Firewall if prompted.
echo.

python ai_server.py
if errorlevel 1 (
    echo.
    echo  Server stopped with an error.
    echo  Make sure Python is installed and try:  python ai_server.py
    pause
)
