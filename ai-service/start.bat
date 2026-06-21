@echo off
echo ========================================
echo Starting AI Service
echo ========================================
echo.

REM Check if venv exists
if not exist "venv\" (
    echo Creating virtual environment...
    python -m venv venv
)

REM Activate venv
call venv\Scripts\activate

REM Install dependencies
echo Installing dependencies...
pip install -r requirements.txt

REM Start service
echo.
echo Starting FastAPI service on http://localhost:8000
echo.
echo Press Ctrl+C to stop
echo.
python run_dev.py
