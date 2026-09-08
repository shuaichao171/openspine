@echo off
rem OpenSpine build script - compiles all sources into bin/ using lib/*.jar
setlocal
cd /d "%~dp0"

powershell.exe -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass ^
  -File "%~dp0scripts\build.ps1"
exit /b %errorlevel%
