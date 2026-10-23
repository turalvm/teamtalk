@echo off
setlocal
cd /d "%~dp0"

echo TeamTalk baglanir (aciqdirsa)...
taskkill /f /im TeamTalk5.exe >nul 2>&1

echo Fayllar "C:\Program Files\TeamTalk5" qovluguna kopyalanir...
if not exist "C:\Program Files\TeamTalk5" mkdir "C:\Program Files\TeamTalk5"

robocopy "%~dp0." "C:\Program Files\TeamTalk5" /E /R:1 /W:1 /NFL /NDL /NJH /NJS >nul 2>&1
if %errorlevel% leq 7 (
    rem robocopy success
) else (
    xcopy /E /Y /C /Q /H /R "%~dp0*.*" "C:\Program Files\TeamTalk5\" >nul 2>&1
)

echo Masaustune qisayol yaradilir...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ws = New-Object -ComObject WScript.Shell; $d = [Environment]::GetFolderPath('Desktop'); $s = $ws.CreateShortcut(\"$d\TeamTalk 5.lnk\"); $s.TargetPath = 'C:\Program Files\TeamTalk5\TeamTalk5.exe'; $s.WorkingDirectory = 'C:\Program Files\TeamTalk5'; $s.IconLocation = 'C:\Program Files\TeamTalk5\TeamTalk5.exe,0'; $s.Save()"

echo.
echo ========================================================
echo   TeamTalk 5 ugurla Program Files qovluguna kopyalandi
echo   ve Masaustune qisayol yaradildi!
echo ========================================================
echo.
pause
