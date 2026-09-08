@echo off
rem OpenSpine Physics/Slider official golden-trace capture and comparison.
setlocal
cd /d "%~dp0"
if not exist bin\com\openspine\io\RuntimeFrameTraceCli.class (
  call build.bat
  if errorlevel 1 exit /b 1
)
where java.exe >nul 2>&1
if errorlevel 1 (
  echo java.exe was not found on PATH.
  exit /b 1
)
java.exe --add-opens java.base/java.lang=ALL-UNNAMED -cp "bin;lib\*" com.openspine.io.RuntimeFrameTraceCli %*
exit /b %errorlevel%
