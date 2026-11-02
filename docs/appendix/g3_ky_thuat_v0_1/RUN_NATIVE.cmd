@echo off
setlocal
cd /d "%~dp0"
py -3 native\launch.py
set TB_EXIT=%ERRORLEVEL%
echo.
echo Exit code: %TB_EXIT%
echo Results are in the evidence folder. G3 is not automatically approved.
pause
exit /b %TB_EXIT%
