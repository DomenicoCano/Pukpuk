package com.example.pukpuk

import org.json.JSONObject

/** JNI bridge to the chimera physical-modelling engine (flatulence creature), same Rust code as the PC version. */
object Chimera {
    init { System.loadLibrary("chimera_android") }

    external fun nativeInit(sampleRate: Double): String
    external fun nativeRender(buf: FloatArray)
    external fun nativeSetParam(index: Int, value: Double)
    external fun nativePlayCall(index: Int)
    external fun nativeStopCall()
    external fun nativeSetGate(level: Double)
    external fun nativeFill()
    external fun nativeTelemetry(arr: DoubleArray): Boolean
}

private val paramRu = mapOf(
    "push" to "Брюшной пресс (натуживание)",
    "eas" to "Наружный сфинктер",
    "ias" to "Внутренний сфинктер",
    "gas" to "Объём газа",
    "spread" to "Раздвинутость ягодиц",
    "size" to "Размер тела",
    "mucus" to "Слизь (адгезия)",
    "toilet" to "На унитазе",
    "seat" to "Утечка у сиденья",
)
private val callRu = mapOf(
    "squeak" to ("Писк" to "Мало газа, тугой сфинктер, ягодицы сжаты: напор едва раскрывает его"),
    "rumble" to ("Рокот" to "Много газа, расслабленный сфинктер, мягкий ровный напор"),
    "staccato" to ("Стаккато" to "Один газовый карман, выпущенный пятью толчками"),
    "clench" to ("Зажим" to "Выпустить, затем сжать наружный сфинктер и оборвать звук"),
    "quiver" to ("Дрожь" to "Наружный сфинктер сжимается и отпускается четыре раза в секунду"),
    "leak" to ("Утечка" to "Расслабленно, ягодицы раздвинуты, лёгкий напор"),
)
private val unitRu = mapOf("cmH₂O" to "см вод. ст.", "mL" to "мл", "kPa" to "кПа", "cm²" to "см²")

data class Param(
    val index: Int, val key: String, val label: String,
    val min: Double, val max: Double, val default: Double,
    val unit: String?, val log: Boolean, val toggle: Boolean,
)

data class Call(val index: Int, val id: String, val name: String, val hint: String)

class CreatureInfo(json: String) {
    val params: List<Param>
    val calls: List<Call>

    init {
        val o = JSONObject(json)
        val p = o.getJSONArray("params")
        params = (0 until p.length()).map {
            val x = p.getJSONObject(it)
            Param(
                it, x.getString("key"), paramRu[x.getString("key")] ?: x.getString("label"), x.getDouble("min"), x.getDouble("max"),
                x.getDouble("default"), if (x.isNull("unit")) null else x.getString("unit").let { u -> unitRu[u] ?: u },
                x.getBoolean("log"), x.getBoolean("toggle"),
            )
        }
        val c = o.getJSONArray("calls")
        calls = (0 until c.length()).map {
            val x = c.getJSONObject(it)
            val id = x.getString("id")
            val ru = callRu[id]
            Call(it, id, ru?.first ?: x.getString("name"), ru?.second ?: x.getString("hint"))
        }
    }
}
