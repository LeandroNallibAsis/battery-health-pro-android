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

echo [1/3] Verificando dispositivos conectados...
"%ADB_CMD%" devices
echo.
echo [2/3] Otorgando permiso permanente DUMP y BATTERY_STATS a Battery Health Pro...
"%ADB_CMD%" shell pm grant com.leandro.batteryhealth android.permission.DUMP
"%ADB_CMD%" shell pm grant com.leandro.batteryhealth android.permission.BATTERY_STATS
echo.
echo [3/3] Sincronizando registros internos del chip EFS (ASOC, Ciclos, 1er Uso, QR)...
"%ADB_CMD%" shell "ASOC=$(dumpsys battery | sed -n 's/.*mSavedBatteryAsoc: \[\([0-9]*\)\].*/\1/p' | head -n 1); USAGE=$(dumpsys battery | sed -n 's/.*mSavedBatteryUsage: \[\([0-9]*\)\].*/\1/p' | head -n 1); FDATE=$(dumpsys battery | sed -n 's/.*FirstUseDate: \[\([0-9]*\)\].*/\1/p' | head -n 1); QR=$(dumpsys battery | grep -oE 'GH[0-9]{2}-[0-9A-Z]+(\+[0-9A-Z]+)*' | head -n 1); [ -n \"$ASOC\" ] && settings put global bh_efs_asoc \"$ASOC\"; [ -n \"$USAGE\" ] && settings put global bh_efs_usage \"$USAGE\"; [ -n \"$FDATE\" ] && settings put global bh_efs_first_use \"$FDATE\"; [ -n \"$QR\" ] && settings put global bh_efs_qr \"$QR\"; am force-stop com.leandro.batteryhealth; am start -n com.leandro.batteryhealth/.MainActivity --es efs_asoc \"$ASOC\" --es efs_usage \"$USAGE\" --es efs_first_use \"$FDATE\" --es efs_qr \"$QR\""

echo.
echo ============================================================================
echo   ¡LISTO! Abre la app "Battery Health Pro" en tu telefono.
echo   Ahora veras el porcentaje exacto ASOC del chip, los ciclos reales de
echo   carga, la fecha de primer uso y el codigo de serie original EFS.
echo ============================================================================
pause
