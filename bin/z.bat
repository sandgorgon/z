@echo off
:: Launcher for z — Plan 9 Acme-inspired text editor
:: z.jar must be in the same directory as this script
java -Dswing.aatext=true -jar "%~dp0z.jar" %*
