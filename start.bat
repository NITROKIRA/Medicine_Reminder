@echo off
echo Compiling Java server...
javac -d backend backend\Main.java
if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed. Make sure Java JDK is installed.
    pause
    exit /b 1
)
echo Starting Medicine Reminder Server...
echo Open http://localhost:8080 in your browser
echo.
java -cp backend Main
pause
