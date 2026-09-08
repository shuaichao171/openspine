@echo off
rem OpenSpine run script - launches the editor or foreground CLI/MCP mode
setlocal
cd /d "%~dp0"
if not exist bin\com\openspine\OpenSpine.class (
  echo bin missing, building first...
  call build.bat
  if errorlevel 1 exit /b 1
)

set "CONSOLE_MODE="
set "FIRST_ARG=%~1"
if "%FIRST_ARG:~0,1%"=="-" set "CONSOLE_MODE=1"
for %%a in (%*) do (
  if /i "%%~a"=="--mcp" set "CONSOLE_MODE=1"
  if /i "%%~a"=="--help" set "CONSOLE_MODE=1"
  if /i "%%~a"=="-h" set "CONSOLE_MODE=1"
  if /i "%%~a"=="--version" set "CONSOLE_MODE=1"
)
if defined CONSOLE_MODE (
  where java.exe >nul 2>&1
  if errorlevel 1 (
    echo java.exe was not found on PATH.
    exit /b 1
  )
  java.exe --add-opens java.base/java.lang=ALL-UNNAMED -cp "bin;lib\*" com.openspine.OpenSpine %*
  exit /b %errorlevel%
)

where javaw.exe >nul 2>&1
if errorlevel 1 (
  echo javaw.exe was not found on PATH.
  exit /b 1
)
start "" /b javaw.exe --add-opens java.base/java.lang=ALL-UNNAMED -cp "bin;lib\*" com.openspine.OpenSpine %*
exit /b 0
