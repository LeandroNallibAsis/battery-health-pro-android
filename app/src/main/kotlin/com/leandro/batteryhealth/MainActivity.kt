package com.leandro.batteryhealth

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leandro.batteryhealth.data.BatteryHealthReader
import com.leandro.batteryhealth.data.BatteryHealthSnapshot
import com.leandro.batteryhealth.ui.theme.BatteryHealthProTheme
import kotlinx.coroutines.delay
import java.util.Locale

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    BatteryHealthReader.ingestIntentExtras(this, intent)
    enableEdgeToEdge()
    setContent {
      BatteryHealthProTheme(darkTheme = true) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = Color(0xFF0D1117),
        ) {
          BatteryHealthDashboard()
        }
      }
    }
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    BatteryHealthReader.ingestIntentExtras(this, intent)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryHealthDashboard() {
  val context = LocalContext.current
  var refreshTrigger by remember { mutableIntStateOf(0) }
  var snapshot by remember { mutableStateOf(BatteryHealthReader.readSnapshot(context)) }
  var showAdbDialog by remember { mutableStateOf(false) }
  var showCapacityDialog by remember { mutableStateOf(false) }

  // Auto-refresh live telemetry every 2 seconds
  LaunchedEffect(refreshTrigger) {
    while (true) {
      snapshot = BatteryHealthReader.readSnapshot(context)
      delay(2000L)
    }
  }

  Scaffold(
    containerColor = Color(0xFF0D1117),
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "Battery Health Pro",
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 20.sp,
            )
            Text(
              text = "${snapshot.deviceModel} • ${snapshot.androidVersion}",
              color = Color(0xFF8B949E),
              fontSize = 12.sp,
            )
          }
        },
        actions = {
          TextButton(
            onClick = {
              snapshot = BatteryHealthReader.readSnapshot(context)
              refreshTrigger++
              Toast.makeText(
                context,
                "Salud verificada: ${snapshot.effectiveHealthPercent}% (${if (snapshot.asocHardwarePercent != null) "Chip EFS Oficial" else "Contador mAh"})",
                Toast.LENGTH_SHORT,
              ).show()
            },
          ) {
            Text("Actualizar", color = Color(0xFF3FB950), fontWeight = FontWeight.Bold)
          }
          TextButton(onClick = { showCapacityDialog = true }) {
            Text("mAh Fábrica", color = Color(0xFF58A6FF), fontWeight = FontWeight.SemiBold)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = Color(0xFF161B22),
        ),
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // 1. Hero Health Card (iPhone style "Condición de Batería")
      HealthHeroCard(snapshot = snapshot)

      // 2. Mode & Precision Status Banner
      ModeStatusBanner(
        snapshot = snapshot,
        onOpenAdbGuide = { showAdbDialog = true },
      )

      // 3. Factory vs Real Capacity & Cycles Card
      CapacityAndCyclesCard(snapshot = snapshot)

      // 4. Live Electrical Telemetry Card (Current, Voltage, Power, Temperature)
      LiveTelemetryCard(snapshot = snapshot)

      // 5. EFS Chip Laboratory Data (First Use Date, Part Number, BSOH, Serial)
      EfsHardwareCard(
        snapshot = snapshot,
        onOpenAdbGuide = { showAdbDialog = true },
      )

      Spacer(modifier = Modifier.height(16.dp))
    }
  }

  if (showAdbDialog) {
    AdbPermissionDialog(
      onDismiss = { showAdbDialog = false },
      onCopyCommand = {
        val cmd = "adb shell pm grant com.leandro.batteryhealth android.permission.DUMP"
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Comando ADB", cmd))
        Toast.makeText(context, "Comando ADB copiado al portapapeles", Toast.LENGTH_SHORT).show()
      },
    )
  }

  if (showCapacityDialog) {
    CustomCapacityDialog(
      currentDesignMah = snapshot.designCapacityMah,
      onDismiss = { showCapacityDialog = false },
      onSave = { newMah ->
        BatteryHealthReader.setCustomDesignCapacity(context, newMah)
        refreshTrigger++
        showCapacityDialog = false
      },
    )
  }
}

@Composable
fun HealthHeroCard(snapshot: BatteryHealthSnapshot) {
  val healthColor = when {
    snapshot.effectiveHealthPercent >= 88 -> Color(0xFF2EA043) // Green
    snapshot.effectiveHealthPercent >= 80 -> Color(0xFF3FB950) // Light Green
    snapshot.effectiveHealthPercent >= 74 -> Color(0xFFD29922) // Amber
    else -> Color(0xFFF85149) // Red
  }

  val animatedProgress by animateFloatAsState(
    targetValue = (snapshot.effectiveHealthPercent / 100f).coerceIn(0f, 1f),
    animationSpec = tween(durationMillis = 800),
    label = "healthProgress",
  )

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = "CONDICIÓN DE SALUD DE LA BATERÍA",
        color = Color(0xFF8B949E),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
      )

      Spacer(modifier = Modifier.height(18.dp))

      Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
        CircularProgressIndicator(
          progress = { 1f },
          modifier = Modifier.fillMaxSize(),
          color = Color(0xFF21262D),
          strokeWidth = 14.dp,
        )
        CircularProgressIndicator(
          progress = { animatedProgress },
          modifier = Modifier.fillMaxSize(),
          color = healthColor,
          strokeWidth = 14.dp,
          strokeCap = StrokeCap.Round,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = "${snapshot.effectiveHealthPercent}%",
            color = Color.White,
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold,
          )
          Text(
            text = if (snapshot.asocHardwarePercent != null) "ASOC Oficial" else "Capacidad Real",
            color = healthColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      Text(
        text = snapshot.recommendationTitle,
        color = Color.White,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = snapshot.recommendationBody,
        color = Color(0xFF8B949E),
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        lineHeight = 18.sp,
      )
    }
  }
}

@Composable
fun ModeStatusBanner(
  snapshot: BatteryHealthSnapshot,
  onOpenAdbGuide: () -> Unit,
) {
  val isLabMode = snapshot.hasDumpPermission
  val borderColor = if (isLabMode) Color(0xFF238636) else Color(0xFF388BFD)
  val badgeBg = if (isLabMode) Color(0xFF12261E) else Color(0xFF121D2F)

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, borderColor, RoundedCornerShape(14.dp)),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = badgeBg),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(if (isLabMode) Color(0xFF3FB950) else Color(0xFF58A6FF)),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = snapshot.dataSourceMode,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
          )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = if (isLabMode) {
            "Permiso DUMP activo: leyendo registros internos del chip Fuel Gauge / EFS."
          } else {
            "Leyendo contador físico de culombios (mAh). Activa el permiso ADB para ver ciclos EFS y fecha de 1er uso."
          },
          color = Color(0xFFB1BAC4),
          fontSize = 12.sp,
          lineHeight = 16.sp,
        )
      }
      if (!isLabMode) {
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(
          onClick = onOpenAdbGuide,
          colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF58A6FF)),
        ) {
          Text("Activar EFS", fontSize = 12.sp)
        }
      }
    }
  }
}

@Composable
fun CapacityAndCyclesCard(snapshot: BatteryHealthSnapshot) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "CAPACIDAD FÍSICA Y CICLOS DE VIDA",
        color = Color(0xFF58A6FF),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
      )

      MetricRow(
        label = "Capacidad de Diseño (Fábrica)",
        value = "${snapshot.designCapacityMah} mAh",
        subValue = "100% original",
      )
      HorizontalDivider(color = Color(0xFF21262D))

      val realCapacityFromAsoc = if (snapshot.asocHardwarePercent != null) {
        (snapshot.designCapacityMah * snapshot.asocHardwarePercent) / 100
      } else {
        snapshot.estimatedFullCapacityMah
      }

      MetricRow(
        label = "Capacidad Máxima Actual (100%)",
        value = "~$realCapacityFromAsoc mAh",
        subValue = "Retención real hoy",
        valueColor = Color(0xFF3FB950),
      )
      HorizontalDivider(color = Color(0xFF21262D))

      MetricRow(
        label = "Carga Almacenada Ahora (${snapshot.levelPercent}%)",
        value = if (snapshot.chargeCounterMah > 0) "${snapshot.chargeCounterMah} mAh" else "No reportado",
        subValue = "Contador de Culombios",
      )
      HorizontalDivider(color = Color(0xFF21262D))

      val cyclesText = when {
        snapshot.cycleCount != null -> String.format(Locale.US, "%.1f ciclos", snapshot.cycleCount)
        else -> "Requiere permiso DUMP"
      }
      val cyclesSub = when {
        snapshot.rawUsageCounter != null -> "Uso acumulado: ${snapshot.rawUsageCounter}% descargado"
        snapshot.cycleCount != null -> "Reportado por el sistema"
        else -> "Toca 'Activar EFS' arriba"
      }

      MetricRow(
        label = "Ciclos de Carga Acumulados",
        value = cyclesText,
        subValue = cyclesSub,
        valueColor = if (snapshot.cycleCount != null && snapshot.cycleCount > 800) Color(0xFFD29922) else Color.White,
      )
    }
  }
}

@Composable
fun LiveTelemetryCard(snapshot: BatteryHealthSnapshot) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "TELEMETRÍA ELÉCTRICA EN VIVO",
          color = Color(0xFF58A6FF),
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.8.sp,
        )
        Text(
          text = "${snapshot.levelPercent}% • ${snapshot.statusText}",
          color = if (snapshot.isCharging) Color(0xFF3FB950) else Color(0xFF8B949E),
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
        )
      }

      LinearProgressIndicator(
        progress = { (snapshot.levelPercent / 100f).coerceIn(0f, 1f) },
        modifier = Modifier
          .fillMaxWidth()
          .height(8.dp)
          .clip(RoundedCornerShape(4.dp)),
        color = if (snapshot.isCharging) Color(0xFF3FB950) else Color(0xFF58A6FF),
        trackColor = Color(0xFF21262D),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        val currentPrefix = if (snapshot.currentNowMa > 0) "+" else ""
        TelemetryBox(
          modifier = Modifier.weight(1f),
          title = "Corriente",
          value = "$currentPrefix${snapshot.currentNowMa} mA",
          subtitle = if (snapshot.isCharging) "Flujo de entrada" else "Consumo actual",
          valueColor = if (snapshot.isCharging) Color(0xFF3FB950) else Color(0xFFFFA657),
        )
        TelemetryBox(
          modifier = Modifier.weight(1f),
          title = "Potencia",
          value = String.format(Locale.US, "%.2f W", snapshot.powerWatts),
          subtitle = "${snapshot.voltageMv} mV",
          valueColor = Color.White,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        val tempColor = when {
          snapshot.temperatureCelsius >= 40.0 -> Color(0xFFF85149)
          snapshot.temperatureCelsius >= 36.0 -> Color(0xFFD29922)
          else -> Color(0xFF3FB950)
        }
        TelemetryBox(
          modifier = Modifier.weight(1f),
          title = "Temperatura",
          value = String.format(Locale.US, "%.1f °C", snapshot.temperatureCelsius),
          subtitle = if (snapshot.temperatureCelsius < 37.0) "Rango frío/óptimo" else "Templada",
          valueColor = tempColor,
        )
        TelemetryBox(
          modifier = Modifier.weight(1f),
          title = "Química / Estado",
          value = snapshot.technology,
          subtitle = snapshot.healthStatusText,
          valueColor = Color.White,
        )
      }
    }
  }
}

@Composable
fun EfsHardwareCard(
  snapshot: BatteryHealthSnapshot,
  onOpenAdbGuide: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "DATOS INTERNOS DEL CHIP (EFS / DUMPSYS)",
          color = Color(0xFF58A6FF),
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.8.sp,
        )
        TextButton(onClick = onOpenAdbGuide) {
          Text("¿Cómo funciona?", fontSize = 12.sp, color = Color(0xFF8B949E))
        }
      }

      MetricRow(
        label = "Salud ASOC Grabada en Chip",
        value = snapshot.asocHardwarePercent?.let { "$it%" } ?: "No disponible / Estándar",
        subValue = "Absolute State of Charge (EFS)",
      )
      HorizontalDivider(color = Color(0xFF21262D))

      MetricRow(
        label = "Fecha de Primer Uso (Activación)",
        value = snapshot.firstUseDate ?: "No disponible sin DUMP",
        subValue = "Grabada en memoria EFS",
      )
      HorizontalDivider(color = Color(0xFF21262D))

      MetricRow(
        label = "Número de Pieza Original (OEM)",
        value = snapshot.partNumber ?: "Genérico / Estándar",
        subValue = snapshot.serialQrRaw?.let { "QR: $it" } ?: "Lectura de serie EFS",
      )
    }
  }
}

@Composable
fun MetricRow(
  label: String,
  value: String,
  subValue: String,
  valueColor: Color = Color.White,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = label,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = subValue,
        color = Color(0xFF8B949E),
        fontSize = 12.sp,
      )
    }
    Text(
      text = value,
      color = valueColor,
      fontSize = 15.sp,
      fontWeight = FontWeight.Bold,
    )
  }
}

@Composable
fun TelemetryBox(
  title: String,
  value: String,
  subtitle: String,
  valueColor: Color,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(Color(0xFF0D1117))
      .border(1.dp, Color(0xFF21262D), RoundedCornerShape(12.dp))
      .padding(12.dp),
  ) {
    Text(
      text = title,
      color = Color(0xFF8B949E),
      fontSize = 12.sp,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = value,
      color = valueColor,
      fontSize = 18.sp,
      fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = subtitle,
      color = Color(0xFF8B949E),
      fontSize = 11.sp,
      maxLines = 1,
    )
  }
}

@Composable
fun AdbPermissionDialog(
  onDismiss: () -> Unit,
  onCopyCommand: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Color(0xFF161B22),
    title = {
      Text(
        text = "Modo Laboratorio EFS (Precisión 100%)",
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Por defecto, la app funciona en cualquier Android midiendo los miliamperios-hora (mAh) físicos del chip Fuel Gauge.",
          color = Color(0xFFC9D1D9),
          fontSize = 13.sp,
        )
        Text(
          text = "Para desbloquear los registros internos de fábrica (ASOC %, Ciclos Exactos, Fecha de Primer Uso y QR de pieza original en Samsung, Xiaomi, Pixel, etc.), ejecuta una sola vez desde tu PC por ADB:",
          color = Color(0xFFC9D1D9),
          fontSize = 13.sp,
        )
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0D1117))
            .padding(10.dp),
        ) {
          Text(
            text = "adb shell pm grant com.leandro.batteryhealth android.permission.DUMP",
            color = Color(0xFF79C0FF),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
          )
        }
        Text(
          text = "¡El permiso queda guardado permanentemente aunque reinicies el teléfono!",
          color = Color(0xFF3FB950),
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
        )
      }
    },
    confirmButton = {
      Button(onClick = onCopyCommand) {
        Text("Copiar Comando ADB")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cerrar", color = Color(0xFF8B949E))
      }
    },
  )
}

@Composable
fun CustomCapacityDialog(
  currentDesignMah: Int,
  onDismiss: () -> Unit,
  onSave: (Int) -> Unit,
) {
  var textValue by remember { mutableStateOf(currentDesignMah.toString()) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Color(0xFF161B22),
    title = {
      Text(
        text = "Capacidad de Fábrica (mAh)",
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "La app detecta automáticamente la capacidad de fábrica desde el perfil interno de Android. Si en otro teléfono deseas ajustarla manualmente al valor comercial (ej. 4500 o 5000 mAh), ingrésalo aquí:",
          color = Color(0xFFC9D1D9),
          fontSize = 13.sp,
        )
        OutlinedTextField(
          value = textValue,
          onValueChange = { textValue = it.filter { ch -> ch.isDigit() } },
          label = { Text("Capacidad en mAh (ej. 4500)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val parsed = textValue.toIntOrNull() ?: currentDesignMah
          onSave(parsed)
        },
      ) {
        Text("Guardar")
      }
    },
    dismissButton = {
      TextButton(
        onClick = {
          onSave(0) // Reset to auto-detect
        },
      ) {
        Text("Automático", color = Color(0xFF58A6FF))
      }
    },
  )
}
