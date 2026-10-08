@echo off
REM OpenFable Launcher - Launches with admin privileges in windowed mode
REM Check for admin rights
net session >nul 2>&1
if %errorLevel% == 0 (
    REM Already admin - launch directly
    start "OpenFable" /normal "C:\OpenFable\packages\opencode\dist\openfable-windows-x64\bin\openfable.exe" %*
) else (
    REM Request admin elevation
    powershell -Command "Start-Process cmd -ArgumentList '/c start \"OpenFable\" /normal \"C:\OpenFable\packages\opencode\dist\openfable-windows-x64\bin\openfable.exe\" %*' -Verb RunAs"
)
