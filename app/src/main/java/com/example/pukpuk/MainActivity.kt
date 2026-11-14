package com.example.pukpuk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.pukpuk.ui.theme.PukpukTheme
import kotlinx.coroutines.delay
import kotlin.math.exp
import kotlin.math.ln

class MainActivity : ComponentActivity() {
    private lateinit var audio: AudioEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        audio = AudioEngine(applicationContext)
        setContent {
            PukpukTheme {
                Scaffold(Modifier.fillMaxSize()) { Screen(audio, Modifier.padding(it)) }
            }
        }
    }

    override fun onStart() { super.onStart(); audio.start() }
    override fun onStop() { super.onStop(); audio.stop() }
}

@Composable
fun Screen(audio: AudioEngine, modifier: Modifier = Modifier) {
    val info = audio.info
    val values = remember { mutableStateListOf<Double>().apply { addAll(info.params.map { it.default }) } }
    var level by remember { mutableDoubleStateOf(0.0) }
    var active by remember { mutableIntStateOf(-1) }
    var dragging by remember { mutableIntStateOf(-1) }
    // sliders follow the engine (calls move the controls); the one being dragged keeps the finger's value
    LaunchedEffect(Unit) {
        while (true) {
            level = audio.outRms
            active = audio.activeCall
            val live = audio.params
            if (live.size == values.size) for (i in live.indices) if (i != dragging && !info.params[i].toggle) values[i] = live[i]
            delay(33)
        }
    }

    Column(
        modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Пукпук", style = MaterialTheme.typography.headlineMedium)
        Text("Homo sapiens, flatus ani — физическая модель", style = MaterialTheme.typography.bodySmall)

        Meter(level)

        Text("Звуки", style = MaterialTheme.typography.titleMedium)
        info.calls.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    Button(
                        onClick = { audio.playCall(c.index) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp),
                        colors = if (active == c.index) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        else ButtonDefaults.buttonColors(),
                    ) { Text(c.name, maxLines = 1) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        OutlinedButton(onClick = { audio.stopCall() }, modifier = Modifier.fillMaxWidth()) { Text("Стоп") }

        HoldButton(onDown = { audio.hold(true) }, onUp = { audio.hold(false) })

        var volume by remember { mutableFloatStateOf(audio.gain) }
        Text("Громкость: ${"%.1f".format(volume)}×", style = MaterialTheme.typography.bodyMedium)
        Slider(value = volume, valueRange = 0.5f..4f, onValueChange = { volume = it; audio.gain = it })

        Text("Параметры", style = MaterialTheme.typography.titleMedium)
        info.params.forEach { p ->
            val v = values[p.index]
            if (p.toggle) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.label, Modifier.weight(1f))
                    Switch(checked = v > 0.5, onCheckedChange = {
                        val nv = if (it) 1.0 else 0.0
                        values[p.index] = nv
                        audio.setParam(p.index, nv)
                    })
                }
            } else {
                Text("${p.label}: ${fmt(v)}${p.unit?.let { " $it" } ?: ""}", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = toSlider(p, v),
                    onValueChange = { s ->
                        dragging = p.index
                        val nv = fromSlider(p, s)
                        values[p.index] = nv
                        audio.setParam(p.index, nv)
                    },
                    onValueChangeFinished = { dragging = -1 },
                )
            }
        }
        TextButton(onClick = {
            info.params.forEach { values[it.index] = it.default; audio.setParam(it.index, it.default) }
        }) { Text("Сбросить") }
    }
}

private fun toSlider(p: Param, v: Double): Float =
    (if (p.log) (ln(v) - ln(p.min)) / (ln(p.max) - ln(p.min)) else (v - p.min) / (p.max - p.min))
        .toFloat().coerceIn(0f, 1f)

private fun fromSlider(p: Param, s: Float): Double =
    if (p.log) exp(ln(p.min) + s * (ln(p.max) - ln(p.min))) else p.min + s * (p.max - p.min)

private fun fmt(v: Double) = if (v >= 100) "%.0f".format(v) else "%.2f".format(v)

@Composable
fun Meter(level: Double) {
    Canvas(Modifier.fillMaxWidth().height(24.dp)) {
        drawRect(Color(0x22000000), size = size)
        val w = (level * 12).coerceIn(0.0, 1.0).toFloat() * size.width
        drawRect(Color(0xFF8D6E63), size = Size(w, size.height))
    }
}

@Composable
fun HoldButton(onDown: () -> Unit, onUp: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (pressed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().height(72.dp).pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown()
                pressed = true; onDown()
                waitForUpOrCancellation()
                pressed = false; onUp()
            }
        },
    ) { Box(contentAlignment = Alignment.Center) { Text("Держать (напряжение)") } }
}
