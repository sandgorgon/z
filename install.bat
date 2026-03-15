@echo off
:: Installer for z — Plan 9 Acme-inspired text editor
:: Installs z.jar and z.bat to %LOCALAPPDATA%\z

setlocal

set INSTALL_DIR=%LOCALAPPDATA%\z
set SCRIPT_DIR=%~dp0
set ICON_FILE=%INSTALL_DIR%\z.ico
set SHORTCUT_DIR=%APPDATA%\Microsoft\Windows\Start Menu\Programs
set SHORTCUT=%SHORTCUT_DIR%\z.lnk

echo Installing z...
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"

copy /Y "%SCRIPT_DIR%z.jar" "%INSTALL_DIR%\z.jar" >nul
copy /Y "%SCRIPT_DIR%z.bat" "%INSTALL_DIR%\z.bat" >nul
copy /Y "%SCRIPT_DIR%src\main\resources\images\z.ico" "%ICON_FILE%" >nul

echo Creating Start Menu shortcut...
powershell -NoProfile -Command ^
  "$ws = New-Object -ComObject WScript.Shell;" ^
  "$s = $ws.CreateShortcut('%SHORTCUT%');" ^
  "$s.TargetPath = '%INSTALL_DIR%\z.bat';" ^
  "$s.IconLocation = '%ICON_FILE%';" ^
  "$s.Description = 'Plan 9 Acme-inspired text editor';" ^
  "$s.Save()"

echo.
echo Done.
echo   JAR:      %INSTALL_DIR%\z.jar
echo   Launcher: %INSTALL_DIR%\z.bat
echo   Icon:     %ICON_FILE%
echo   Shortcut: %SHORTCUT%
echo.
echo Add %INSTALL_DIR% to your PATH to run z from any directory.
echo Or run directly: java -jar "%INSTALL_DIR%\z.jar"
