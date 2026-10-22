@echo off
setlocal
cd /d "%~dp0"

echo TeamTalk bağlanır (əgər açıqdırsa)...
taskkill /f /im TeamTalk5.exe >nul 2>&1

echo Fayllar "C:\Program Files\TeamTalk5" qovluğuna kopyalanır...
if not exist "C:\Program Files\TeamTalk5" mkdir "C:\Program Files\TeamTalk5"

xcopy /E /Y /I /C /Q "%~dp0*.*" "C:\Program Files\TeamTalk5\" >nul

echo Masaüstünə qısayol əlavə edilir...
powershell -NoProfile -Command "$ws = New-Object -ComObject WScript.Shell; $d = [Environment]::GetFolderPath('Desktop'); $s = $ws.CreateShortcut(\"$d\TeamTalk 5.lnk\"); $s.TargetPath = 'C:\Program Files\TeamTalk5\TeamTalk5.exe'; $s.WorkingDirectory = 'C:\Program Files\TeamTalk5'; $s.IconLocation = 'C:\Program Files\TeamTalk5\TeamTalk5.exe,0'; $s.Save()"

echo.
echo ========================================================
echo   TeamTalk 5 uğurla Program Files qovluğuna kopyalandı
echo   və Masaüstünə qısayol yaradıldı!
echo ========================================================
echo.
pause
