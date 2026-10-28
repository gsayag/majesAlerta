@echo off
chcp 65001 >nul
cd /d "%~dp0"
where py >nul 2>nul
if %errorlevel% equ 0 (
  start "" cmd /c "timeout /t 2 /nobreak >nul & start http://127.0.0.1:8000"
  py -3 server.py
  pause
  exit /b
)
where python >nul 2>nul
if %errorlevel% equ 0 (
  start "" cmd /c "timeout /t 2 /nobreak >nul & start http://127.0.0.1:8000"
  python server.py
) else (
  echo Instala Python 3.10 o posterior desde https://www.python.org/downloads/
  echo Activa la opcion Add Python to PATH y vuelve a abrir este archivo.
)
pause
