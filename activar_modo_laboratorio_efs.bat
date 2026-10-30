@echo off
chcp 65001 >nul
title Battery Health Pro - Activador de Modo Laboratorio EFS (Sin Root)
echo ============================================================================
echo   BATTERY HEALTH PRO - ACTIVADOR DE MODO LABORATORIO EFS (ANDROID / SAMSUNG)
echo ============================================================================
echo.
echo Conecta tu telefono Android por USB (o Depuracion Inalambrica) con
echo la Depuracion USB activada.
echo.

set "ADB_CMD=adb"
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set "ADB_CMD=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
)

echo [1/2] Verificando dispositivos conectados...
"%ADB_CMD%" devices
echo.
echo [2/2] Otorgando permiso permanente DUMP y BATTERY_STATS a Battery Health Pro...
"%ADB_CMD%" shell pm grant com.leandro.batteryhealth android.permission.DUMP
"%ADB_CMD%" shell pm grant com.leandro.batteryhealth android.permission.BATTERY_STATS

echo.
echo ============================================================================
echo   ¡LISTO! Abre la app "Battery Health Pro" en tu telefono.
echo   Ahora veras el porcentaje exacto ASOC del chip, los ciclos reales de
echo   carga, la fecha de primer uso y el codigo de serie original EFS.
echo ============================================================================
pause
