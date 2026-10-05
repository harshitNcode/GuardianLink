@echo off
title GuardianLink Backend Server
cd /d "%~dp0"
echo ========================================================
echo       Starting GuardianLink FastAPI Backend Server
echo ========================================================
echo.
echo Once running, open these in your browser:
echo   - Swagger API Docs: http://localhost:8000/docs
echo   - ReDoc:            http://localhost:8000/redoc
echo.
echo Keep this terminal window OPEN while testing!
echo Press Ctrl+C to stop the server.
echo ========================================================
echo.

.\venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
pause
