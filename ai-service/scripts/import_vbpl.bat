@echo off
REM Script don gian de import van ban phap luat
REM Double-click de chay!

echo ========================================
echo IMPORT VAN BAN PHAP LUAT VAO AI
echo ========================================
echo.

REM Kiem tra AI service co dang chay khong
echo [1/3] Kiem tra AI Service...
curl -s http://localhost:8000/health >nul 2>&1
if %errorlevel% neq 0 (
    echo [!] AI Service chua chay!
    echo     Vui long chay: cd ai-service ^&^& docker-compose up -d
    pause
    exit /b 1
)
echo [OK] AI Service dang chay

echo.
echo [2/3] Import PDF files...
echo     Thu muc: C:\Users\DELL\Documents\VBPLHHL
echo.

REM Chay script Python
python import_legal_docs.py --source "C:\Users\DELL\Documents\VBPLHHL" --max-workers 3

echo.
echo [3/3] Hoan thanh!
echo.
echo Xem ket qua:
echo - Log: import_legal_docs.log
echo - JSON: import_results.json
echo.

pause
