package com.leandro.batteryhealth.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.math.abs
import kotlin.math.roundToInt

data class BatteryHealthSnapshot(
  val deviceModel: String,
  val deviceManufacturer: String,
  val androidVersion: String,
  val hasDumpPermission: Boolean,
  val dataSourceMode: String,
  val levelPercent: Int,
  val statusText: String,
  val isCharging: Boolean,
  val plugTypeText: String,
  val healthStatusText: String,
  val technology: String,
  val temperatureCelsius: Double,
  val voltageMv: Int,
  val currentNowMa: Int,
  val powerWatts: Double,
  val chargeCounterMah: Int,
  val designCapacityMah: Int,
  val estimatedFullCapacityMah: Int,
  val effectiveHealthPercent: Int,
  val asocHardwarePercent: Int?,
  val bsohPercent: Int?,
  val cycleCount: Double?,
  val rawUsageCounter: Int?,
  val firstUseDate: String?,
  val partNumber: String?,
  val serialQrRaw: String?,
  val recommendationTitle: String,
  val recommendationBody: String,
)

object BatteryHealthReader {

  private const val PREFS_NAME = "battery_health_prefs"
  private const val KEY_CALIBRATED_FULL_MAH = "calibrated_full_mah"
  private const val KEY_CUSTOM_DESIGN_MAH = "custom_design_mah"
  private const val KEY_CACHED_ASOC = "cached_asoc"
  private const val KEY_CACHED_BSOH = "cached_bsoh"
  private const val KEY_CACHED_USAGE = "cached_usage"
  private const val KEY_CACHED_FIRST_USE = "cached_first_use"
  private const val KEY_CACHED_PART = "cached_part"
  private const val KEY_CACHED_QR = "cached_qr"

  fun ingestIntentExtras(context: Context, intent: Intent?) {
    if (intent == null) return
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val editor = prefs.edit()
    var changed = false

    intent.getStringExtra("efs_asoc")?.toIntOrNull()?.takeIf { it in 1..100 }?.let {
      editor.putInt(KEY_CACHED_ASOC, it)
      changed = true
    }
    intent.getStringExtra("efs_usage")?.toIntOrNull()?.takeIf { it > 0 }?.let {
      editor.putInt(KEY_CACHED_USAGE, it)
      changed = true
    }
    intent.getStringExtra("efs_first_use")?.takeIf { it.isNotBlank() }?.let {
      editor.putString(KEY_CACHED_FIRST_USE, formatFirstUseDate(it))
      changed = true
    }
    intent.getStringExtra("efs_qr")?.takeIf { it.isNotBlank() }?.let { qr ->
      editor.putString(KEY_CACHED_QR, qr)
      Regex("""(GH\d{2}-[0-9A-Z]+)""").find(qr)?.groupValues?.get(1)?.let { part ->
        editor.putString(KEY_CACHED_PART, part)
      }
      changed = true
    }
    if (changed) editor.apply()
  }

  fun readSnapshot(context: Context): BatteryHealthSnapshot {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val levelPercent = if (level >= 0 && scale > 0) ((level * 100f) / scale).roundToInt() else bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
    val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL || plugged > 0

    val plugTypeText = when (plugged) {
      BatteryManager.BATTERY_PLUGGED_AC -> "Cargador CA (Pared)"
      BatteryManager.BATTERY_PLUGGED_USB -> "Puerto USB"
      BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Carga Inalámbrica Qi"
      BatteryManager.BATTERY_PLUGGED_DOCK -> "Base Dock"
      else -> "En Batería"
    }

    val statusText = when (status) {
      BatteryManager.BATTERY_STATUS_CHARGING -> "Cargando ($plugTypeText)"
      BatteryManager.BATTERY_STATUS_DISCHARGING -> "Descargando (En uso)"
      BatteryManager.BATTERY_STATUS_FULL -> "Batería Completa (100%)"
      BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Conectado sin cargar"
      else -> "Activo"
    }

    val healthInt = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
    val healthStatusText = when (healthInt) {
      BatteryManager.BATTERY_HEALTH_GOOD -> "Buena (Sin fallos eléctricos)"
      BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Sobrecalentamiento"
      BatteryManager.BATTERY_HEALTH_DEAD -> "Agotada / Reemplazo urgente"
      BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Sobretensión"
      BatteryManager.BATTERY_HEALTH_COLD -> "Temperatura muy baja"
      else -> "Normal"
    }

    val technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() } ?: "Li-ion"
    val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
    val temperatureCelsius = tempTenths / 10.0

    val rawVoltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
    val voltageMv = if (rawVoltage in 1..20) rawVoltage * 1000 else rawVoltage

    // Charge Counter (µAh -> mAh)
    val chargeCounterUah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
    val chargeCounterMah = if (chargeCounterUah > 10_000) {
      (chargeCounterUah / 1000.0).roundToInt()
    } else if (chargeCounterUah in 1..10_000) {
      chargeCounterUah
    } else {
      0
    }

    // Current Now (µA or mA -> mA)
    val rawCurrent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
    val currentNowMa = when {
      abs(rawCurrent) > 25_000 -> (rawCurrent / 1000.0).roundToInt()
      else -> rawCurrent
    }

    val powerWatts = (abs(currentNowMa) / 1000.0) * (voltageMv / 1000.0)

    // Design Capacity (mAh)
    val customDesign = prefs.getInt(KEY_CUSTOM_DESIGN_MAH, 0)
    val designCapacityMah = if (customDesign > 500) {
      customDesign
    } else {
      readDesignCapacityMah(context)
    }

    // Read EFS / DUMP data from Settings.Global bridge, SharedPreferences, dumpsys, or sysfs
    val permGranted = context.checkCallingOrSelfPermission(android.Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED
    val dumpData = readEfsAndDumpData(context, permGranted)
    val hasEfsData = dumpData.asocPercent != null || dumpData.rawUsageCounter != null || dumpData.firstUseDate != null

    // Android 14+ standard cycle count extra fallback
    val intentCycles = intent?.getIntExtra("android.os.extra.CYCLE_COUNT", -1)?.takeIf { it > 0 }
    val finalCycles = dumpData.cycleCount ?: intentCycles?.toDouble()

    // Calculate estimated full capacity (mAh) from Coulomb Counter ONLY when not fast-charging or at 100%
    val liveEstimatedFullMah = if (chargeCounterMah > 0 && levelPercent >= 20) {
      ((chargeCounterMah.toDouble() / levelPercent.toDouble()) * 100.0).roundToInt()
    } else {
      0
    }

    if (liveEstimatedFullMah in 1000..15000 && ((!isCharging && levelPercent >= 65) || levelPercent == 100)) {
      prefs.edit().putInt(KEY_CALIBRATED_FULL_MAH, liveEstimatedFullMah).apply()
    }
    val savedCalibratedMah = prefs.getInt(KEY_CALIBRATED_FULL_MAH, 0)

    val estimatedFullCapacityMah = when {
      dumpData.asocPercent != null && designCapacityMah > 0 -> ((designCapacityMah * dumpData.asocPercent) / 100.0).roundToInt()
      dumpData.learnedCapacityMah != null && dumpData.learnedCapacityMah > 1000 -> dumpData.learnedCapacityMah
      savedCalibratedMah > 1000 -> savedCalibratedMah
      liveEstimatedFullMah in 1000..15000 -> {
        prefs.edit().putInt(KEY_CALIBRATED_FULL_MAH, liveEstimatedFullMah).apply()
        liveEstimatedFullMah
      }
      else -> designCapacityMah
    }

    val coulombHealthPercent = if (estimatedFullCapacityMah > 0 && designCapacityMah > 0) {
      ((estimatedFullCapacityMah.toDouble() / designCapacityMah.toDouble()) * 100.0).roundToInt().coerceIn(1, 100)
    } else {
      100
    }

    val effectiveHealthPercent = dumpData.asocPercent?.coerceIn(1, 100) ?: coulombHealthPercent

    val dataSourceMode = when {
      hasEfsData -> "Modo Laboratorio EFS Activo (Salud Oficial del Chip)"
      permGranted -> "Modo Avanzado DUMP + Contador de Culombios"
      else -> "Modo Universal Android (Fuel Gauge Estabilizado)"
    }

    val (recTitle, recBody) = buildRecommendation(effectiveHealthPercent, finalCycles, temperatureCelsius)

    return BatteryHealthSnapshot(
      deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
      deviceManufacturer = Build.MANUFACTURER,
      androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
      hasDumpPermission = hasEfsData || permGranted,
      dataSourceMode = dataSourceMode,
      levelPercent = levelPercent,
      statusText = statusText,
      isCharging = isCharging,
      plugTypeText = plugTypeText,
      healthStatusText = healthStatusText,
      technology = technology,
      temperatureCelsius = temperatureCelsius,
      voltageMv = voltageMv,
      currentNowMa = currentNowMa,
      powerWatts = powerWatts,
      chargeCounterMah = chargeCounterMah,
      designCapacityMah = designCapacityMah,
      estimatedFullCapacityMah = estimatedFullCapacityMah,
      effectiveHealthPercent = effectiveHealthPercent,
      asocHardwarePercent = dumpData.asocPercent,
      bsohPercent = dumpData.bsohPercent,
      cycleCount = finalCycles,
      rawUsageCounter = dumpData.rawUsageCounter,
      firstUseDate = dumpData.firstUseDate,
      partNumber = dumpData.partNumber,
      serialQrRaw = dumpData.serialQrRaw,
      recommendationTitle = recTitle,
      recommendationBody = recBody,
    )
  }

  fun setCustomDesignCapacity(context: Context, capacityMah: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    if (capacityMah <= 0) {
      prefs.edit().remove(KEY_CUSTOM_DESIGN_MAH).apply()
    } else {
      prefs.edit().putInt(KEY_CUSTOM_DESIGN_MAH, capacityMah).apply()
    }
  }

  private fun formatFirstUseDate(rawDate: String): String {
    val clean = rawDate.trim()
    return if (clean.length == 8 && clean.all { it.isDigit() }) {
      val year = clean.substring(0, 4)
      val month = clean.substring(4, 6)
      val day = clean.substring(6, 8)
      "$day/$month/$year"
    } else {
      clean
    }
  }

  @SuppressLint("PrivateApi")
  private fun readDesignCapacityMah(context: Context): Int {
    val model = Build.MODEL.uppercase()
    if (model.contains("SM-G990")) return 4500 // Samsung Galaxy S21 FE 5G
    if (model.contains("T610")) return 5000    // TCL 40 SE

    try {
      val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
      val constructor = powerProfileClass.getConstructor(Context::class.java)
      val powerProfile = constructor.newInstance(context)
      val method = powerProfileClass.getMethod("getAveragePower", String::class.java)
      val capacity = (method.invoke(powerProfile, "battery.capacity") as? Double)?.roundToInt() ?: 0
      if (capacity in 1000..20000) {
        return capacity
      }
    } catch (_: Throwable) {
    }

    val sysfsDesign = readSysfsInt("/sys/class/power_supply/battery/charge_full_design")
    if (sysfsDesign != null) {
      return if (sysfsDesign > 100_000) sysfsDesign / 1000 else sysfsDesign
    }

    return 4500
  }

  private data class DumpParseResult(
    val asocPercent: Int? = null,
    val bsohPercent: Int? = null,
    val rawUsageCounter: Int? = null,
    val cycleCount: Double? = null,
    val firstUseDate: String? = null,
    val partNumber: String? = null,
    val serialQrRaw: String? = null,
    val learnedCapacityMah: Int? = null,
  )

  private fun readEfsAndDumpData(context: Context, hasDumpPerm: Boolean): DumpParseResult {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val cr = context.contentResolver

    var asoc: Int? = null
    var bsoh: Int? = null
    var rawUsage: Int? = null
    var cycles: Double? = null
    var firstUse: String? = null
    var partNumber: String? = null
    var qrRaw: String? = null
    var learnedMah: Int? = null

    // 1. Read Settings.Global EFS bridge keys (world-readable by any app without SELinux file blocks)
    try {
      Settings.Global.getString(cr, "bh_efs_asoc")?.trim()?.toIntOrNull()?.takeIf { it in 1..100 }?.let {
        asoc = it
      }
      Settings.Global.getString(cr, "bh_efs_bsoh")?.trim()?.toIntOrNull()?.takeIf { it in 1..100 }?.let {
        bsoh = it
      }
      Settings.Global.getString(cr, "bh_efs_usage")?.trim()?.toIntOrNull()?.takeIf { it > 0 }?.let {
        rawUsage = it
        cycles = it / 100.0
      }
      Settings.Global.getString(cr, "bh_efs_first_use")?.trim()?.takeIf { it.isNotBlank() }?.let {
        firstUse = formatFirstUseDate(it)
      }
      Settings.Global.getString(cr, "bh_efs_qr")?.trim()?.takeIf { it.isNotBlank() }?.let { qr ->
        qrRaw = qr
        Regex("""(GH\d{2}-[0-9A-Z]+)""").find(qr)?.groupValues?.get(1)?.let { part ->
          partNumber = part
        }
      }
    } catch (_: Throwable) {
    }

    // 2. Also try dumpsys battery if permitted by SELinux
    if (hasDumpPerm && asoc == null) {
      try {
        val process = Runtime.getRuntime().exec(arrayOf("dumpsys", "battery"))
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val output = reader.readText()
        reader.close()

        Regex("""mSavedBatteryAsoc:\s*\[(\d+)]""").find(output)?.groupValues?.get(1)?.toIntOrNull()?.let {
          if (it in 1..100) asoc = it
        }
        Regex("""mSavedBatteryUsage:\s*\[(\d+)]""").find(output)?.groupValues?.get(1)?.toIntOrNull()?.let {
          rawUsage = it
          cycles = it / 100.0
        }
        Regex("""FirstUseDate:\s*\[(\d{8})]""").find(output)?.groupValues?.get(1)?.let {
          firstUse = formatFirstUseDate(it)
        }
        Regex("""QrData(?::\s*\[|\s+efsValue:)([^\s\]]+)""").find(output)?.groupValues?.get(1)?.let { qr ->
          qrRaw = qr
          Regex("""(GH\d{2}-[0-9A-Z]+)""").find(qr)?.groupValues?.get(1)?.let { part ->
            partNumber = part
          }
        }
      } catch (_: Throwable) {
      }
    }

    // 3. Persist/restore in SharedPreferences
    val editor = prefs.edit()
    if (asoc != null) {
      editor.putInt(KEY_CACHED_ASOC, asoc)
    } else if (prefs.contains(KEY_CACHED_ASOC)) {
      asoc = prefs.getInt(KEY_CACHED_ASOC, -1).takeIf { it in 1..100 }
    }
    if (bsoh != null) {
      editor.putInt(KEY_CACHED_BSOH, bsoh)
    } else if (prefs.contains(KEY_CACHED_BSOH)) {
      bsoh = prefs.getInt(KEY_CACHED_BSOH, -1).takeIf { it in 1..100 }
    }
    if (rawUsage != null) {
      editor.putInt(KEY_CACHED_USAGE, rawUsage)
    } else if (prefs.contains(KEY_CACHED_USAGE)) {
      rawUsage = prefs.getInt(KEY_CACHED_USAGE, -1).takeIf { it > 0 }
      if (rawUsage != null) cycles = rawUsage / 100.0
    }
    if (firstUse != null) {
      editor.putString(KEY_CACHED_FIRST_USE, firstUse)
    } else {
      firstUse = prefs.getString(KEY_CACHED_FIRST_USE, null)
    }
    if (partNumber != null) {
      editor.putString(KEY_CACHED_PART, partNumber)
    } else {
      partNumber = prefs.getString(KEY_CACHED_PART, null)
    }
    if (qrRaw != null) {
      editor.putString(KEY_CACHED_QR, qrRaw)
    } else {
      qrRaw = prefs.getString(KEY_CACHED_QR, null)
    }
    editor.apply()

    // 4. Fallback to sysfs if any metric wasn't found
    if (asoc == null) {
      asoc = readSysfsInt("/sys/class/power_supply/battery/fg_asoc")?.takeIf { it in 1..100 }
    }
    if (cycles == null) {
      readSysfsInt("/sys/class/power_supply/battery/cycle_count")?.takeIf { it > 0 }?.let {
        cycles = it.toDouble()
      }
    }
    val sysfsFull = readSysfsInt("/sys/class/power_supply/battery/charge_full")
    if (sysfsFull != null && sysfsFull > 1000) {
      learnedMah = if (sysfsFull > 100_000) sysfsFull / 1000 else sysfsFull
    }

    return DumpParseResult(
      asocPercent = asoc,
      bsohPercent = bsoh,
      rawUsageCounter = rawUsage,
      cycleCount = cycles,
      firstUseDate = firstUse,
      partNumber = partNumber,
      serialQrRaw = qrRaw,
      learnedCapacityMah = learnedMah,
    )
  }

  private fun readSysfsInt(path: String): Int? {
    return try {
      val file = File(path)
      if (file.exists() && file.canRead()) {
        file.readText().trim().toIntOrNull()
      } else {
        null
      }
    } catch (_: Throwable) {
      null
    }
  }

  private fun buildRecommendation(healthPercent: Int, cycles: Double?, tempC: Double): Pair<String, String> {
    if (tempC >= 41.0) {
      return "Temperatura Elevada (${String.format("%.1f", tempC)} °C)" to
        "La batería está caliente en este momento. Evita usar el teléfono mientras carga para prevenir degradación química acelerada."
    }
    return when {
      healthPercent >= 90 -> "Salud Excelente ($healthPercent%)" to
        "Tu batería conserva casi toda su capacidad original de fábrica y entrega voltajes óptimos sin caídas de rendimiento."
      healthPercent in 80..89 -> "Salud Buena - Desgaste Normal ($healthPercent%)" to
        "Al igual que en iPhone, una batería por encima del 80% sigue siendo totalmente funcional. Se estima una reducción del ${100 - healthPercent}% en horas de pantalla respecto al día que salió de la caja."
      healthPercent in 70..79 -> "Degradación Moderada ($healthPercent%)" to
        "La batería ya superó su umbral ideal del 80%. Notarás que el porcentaje baja más rápido en el último 30% de carga. Considera un cambio de batería si necesitas máxima autonomía."
      else -> "Reemplazo de Batería Recomendado ($healthPercent%)" to
        "La celda química presenta desgaste severo y puede experimentar apagones repentinos bajo alto consumo."
    }
  }
}
