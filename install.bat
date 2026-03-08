@echo off
:: Installer for z — Plan 9 Acme-inspired text editor
:: Installs z.jar and z.bat to %LOCALAPPDATA%\z

setlocal

set INSTALL_DIR=%LOCALAPPDATA%\z
set SCRIPT_DIR=%~dp0

echo Installing z...
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"

copy /Y "%SCRIPT_DIR%z.jar" "%INSTALL_DIR%\z.jar" >nul
copy /Y "%SCRIPT_DIR%z.bat" "%INSTALL_DIR%\z.bat" >nul

echo.
echo Done.
echo   JAR:      %INSTALL_DIR%\z.jar
echo   Launcher: %INSTALL_DIR%\z.bat
echo.
echo Add %INSTALL_DIR% to your PATH to run z from any directory.
echo Or run directly: java -jar "%INSTALL_DIR%\z.jar"
