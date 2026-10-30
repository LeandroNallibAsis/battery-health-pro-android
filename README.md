# 🔋 Battery Health Pro para Android

Aplicación nativa para Android desarrollada en **Kotlin + Jetpack Compose** que muestra el **estado de salud real de la batería (`ASOC %` y capacidad física en `mAh`)**, **ciclos de carga acumulados**, **fecha de primer uso**, **código de pieza original (QR EFS)** y **telemetría eléctrica en tiempo real** (corriente en `mA`, potencia en `W`, voltaje en `mV` y temperatura en `°C`).

---

## ✨ Características Principales

1. **Modo Universal (Funciona en cualquier Android sin PC ni Root):**
   - Lee directamente el **Contador Físico de Culombios (`BATTERY_PROPERTY_CHARGE_COUNTER`)** del chip *Fuel Gauge* de la placa base.
   - Obtiene automáticamente la **Capacidad de Diseño de Fábrica (`mAh`)** mediante reflexión sobre el perfil interno del kernel (`com.android.internal.os.PowerProfile`) y nodos `sysfs`.
   - Calcula la **Capacidad Máxima Actual (`mAh`)** y el porcentaje de retención real frente al diseño original.
   - Monitor eléctrico en vivo (actualización cada 2 segundos): flujo de corriente (`+mA` cargando / `-mA` descargando), potencia en Watts (`W`), tensión en milivoltios (`mV`) y temperatura con alertas térmicas.

2. **Modo Laboratorio EFS / Sistema (`android.permission.DUMP` — Precisión 100% Oficial):**
   - Al otorgar por única vez el permiso `DUMP` mediante ADB (o Root), la aplicación interroga directamente los registros protegidos del subsistema de batería (`dumpsys battery` + `sysfs`):
     - **Salud Oficial del Chip (`mSavedBatteryAsoc` / `fg_asoc`)**: El porcentaje exacto de salud calculado por el controlador de hardware (ej. `88%`), idéntico al menú *Condición de batería* de iOS/iPhone.
     - **Ciclos Reales de Carga (`mSavedBatteryUsage` / `cycle_count`)**: Cantidad exacta de ciclos completos acumulados desde que el teléfono salió de la caja (ej. `2507.9 ciclos`).
     - **Fecha de Primer Uso (`FirstUseDate`)**: Fecha exacta grabada en la memoria EFS cuando se encendió la batería por primera vez.
     - **Número de Pieza y Serie (`QrData`)**: Verifica si la batería es la pieza original de fábrica (ej. `GH43-05085A`).

---

## 📲 Descarga e Instalación del APK

1. Descarga e instala el archivo **`BatteryHealthPro-v1.0.0.apk`** incluido en la raíz de este repositorio (o en la sección *Releases*).
2. Ábrelo en cualquier teléfono Android (Android 8.0 / API 26 o superior).
3. *(Opcional - Recomendado para Samsung, Pixel, Xiaomi, etc.)* Para desbloquear los datos internos EFS (**ASOC %, Ciclos de fábrica y Fecha de 1er uso**), ejecuta el script incluido [`activar_modo_laboratorio_efs.bat`](activar_modo_laboratorio_efs.bat) en tu PC o corre este comando por única vez con ADB:

```bash
adb shell pm grant com.leandro.batteryhealth android.permission.DUMP
adb shell pm grant com.leandro.batteryhealth android.permission.BATTERY_STATS
```

*(El permiso queda guardado permanentemente en el teléfono incluso si lo reinicias).*

---

## 🛠️ Compilación desde el Código Fuente

Requisitos:
- **Android SDK** (`compileSdk = 36`, `minSdk = 26`)
- **JDK 17+**

```bash
./gradlew assembleRelease
```

El APK firmado listo para instalar se generará en `app/build/outputs/apk/release/app-release.apk`.
