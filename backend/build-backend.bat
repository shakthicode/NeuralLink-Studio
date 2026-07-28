@echo off
setlocal
pushd "%~dp0"

where g++ >nul 2>&1
if errorlevel 1 (
    echo [ERROR] MinGW g++ was not found in PATH.
    echo Install MSYS2/MinGW-w64 and add its bin directory to PATH.
    pause
    popd
    exit /b 1
)

echo Building C++ simulation backend...
g++ -std=c++17 -O2 -Wall -Wextra -pedantic simulation_backend.cpp -o simulation_backend.exe
if errorlevel 1 (
    echo [ERROR] C++ backend build failed.
    pause
    popd
    exit /b 1
)

echo Backend built successfully: backend\simulation_backend.exe
popd
endlocal
