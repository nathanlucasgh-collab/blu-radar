@echo off
where gradle >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
  echo Gradle is not installed or on PATH. Install Android Studio or Gradle to build this project.
  exit /b 1
)

gradle %*
