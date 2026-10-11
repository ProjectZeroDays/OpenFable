@echo off
REM Agnes Key Rotation Wrapper
REM Rotates to the next Agnes provider and sets it in hermes config.
REM Usage: rotate_agnes.bat [get|advance|status|set N]
setlocal enabledelayedexpansion

set SKILL_DIR=%~dp0
set PROJECT_ROOT=%SKILL_DIR%..\..\
set ROTATE_SCRIPT=%SKILL_DIR%scripts\agnes_key_rotate.py

cd /d "%PROJECT_ROOT%"

if "%~1"=="get" (
    for /f "delims=" %%i in ('python "%ROTATE_SCRIPT%" get-provider') do set "PROV=%%i"
    echo !PROV!
    goto :eof
)

if "%~1"=="advance" (
    for /f "delims=" %%i in ('python "%ROTATE_SCRIPT%" advance') do set "PROV=%%i"
    echo !PROV!
    hermes config set model.provider "custom:!PROV!"
    goto :eof
)

if "%~1"=="status" (
    python "%ROTATE_SCRIPT%" status
    goto :eof
)

if "%~1"=="set" (
    for /f "delims=" %%i in ('python "%ROTATE_SCRIPT%" set-provider %~2') do set "PROV=%%i"
    echo !PROV!
    hermes config set model.provider "custom:!PROV!"
    goto :eof
)

REM Default: advance + set
for /f "delims=" %%i in ('python "%ROTATE_SCRIPT%" advance') do set "PROV=%%i"
echo Rotated to: !PROV!
hermes config set model.provider "custom:!PROV!"
