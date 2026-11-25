package com.dentilike.dentiagenda

import org.json.JSONObject
import java.util.Calendar

/** Una cita tal como la manda DentiAgenda: fecha, hora de inicio y fin, iniciales, motivo, estado y asistencia. */
data class Cita(val d: String, val h: String, val f: String, val p: String, val m: String, val e: String, val a: String, val x: String = "") {
    fun inicio(): Long = epoch(d, h)
    fun fin(): Long { val x = epoch(d, f); return if (x > inicio()) x else inicio() + 30 * 60000L }
    fun corto(): String = if (m.isNotBlank()) "$p · $m" else p

    companion object {
        fun epoch(fecha: String, hora: String): Long {
            return try {
                val pf = fecha.split("-").map { it.toInt() }
                val ph = hora.split(":").map { it.trim().toInt() }
                val c = Calendar.getInstance()
                c.set(pf[0], pf[1] - 1, pf[2], ph[0], ph[1], 0)
                c.set(Calendar.MILLISECOND, 0)
                c.timeInMillis
            } catch (e: Exception) { 0L }
        }
    }
}

data class Fila(val hora: String, val texto: String, val tag: String, val tipo: String)

/** Todo lo que el widget necesita dibujar en este momento. */
data class Vista(
    val emoji: String,
    val mensajes: List<String>,
    val cdTexto: String,
    val cdDetalle: String,      // en qué arco va o qué tratamiento le toca
    val cdGris: Boolean,
    val progreso: Int,
    val progresoTxt: String,
    val filas: List<Fila>,
    val recordatorios: List<String>,
    val citasRestantes: Int,
    val chicoProx: String,
    val chicoNombre: String,
    val noche: Boolean,
    val avatar: Int,
    val siguienteCambio: Long?,
    val mesNombre: String = "",
    val mesIngresos: Long = -1,
    val mesGastos: Long = -1
)

object Estado {

    private fun hoyIso(c: Calendar): String =
        String.format("%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))

    /** Avatar de temporada, con las mismas fechas que DentiAgenda. */
    fun avatar(c: Calendar): Int {
        val m = c.get(Calendar.MONTH) + 1
        val d = c.get(Calendar.DAY_OF_MONTH)
        fun entre(m1: Int, d1: Int, m2: Int, d2: Int): Boolean {
            val x = m * 100 + d; val a = m1 * 100 + d1; val b = m2 * 100 + d2
            return if (a <= b) x in a..b else (x >= a || x <= b)
        }
        return when {
            entre(9, 10, 9, 17) -> R.drawable.av_patrias
            entre(10, 26, 10, 29) -> R.drawable.av_halloween
            entre(10, 30, 11, 2) -> R.drawable.av_muertos
            entre(12, 18, 12, 25) -> R.drawable.av_navidad
            entre(12, 29, 1, 2) -> R.drawable.av_anonuevo
            entre(2, 10, 2, 14) -> R.drawable.av_valentin
            else -> R.drawable.av_normal
        }
    }

    fun calcular(r: JSONObject?, vinculado: Boolean, ahora: Long = System.currentTimeMillis()): Vista {
        val cal = Calendar.getInstance().apply { timeInMillis = ahora }
        val hora = cal.get(Calendar.HOUR_OF_DAY)
        val av = avatar(cal)
        val saludo = when { hora < 12 -> "Buenos días"; hora < 19 -> "Buenas tardes"; else -> "Buenas noches" }

        if (!vinculado) {
            return Vista("👋", listOf("Hola, soy Mikes Molares.", "Para mostrarle su agenda, abra DentiAgenda → Ajustes → Widget Mikes Molares → Vincular."),
                "Toque aquí para abrir DentiAgenda", "", true, 0, "", emptyList(), emptyList(), 0, "Sin vincular", "Abra DentiAgenda", false, av, null)
        }
        if (r == null) {
            return Vista("⏳", listOf("Conectando con DentiAgenda…", "Si tarda, abra DentiAgenda una vez para que me mande su agenda."),
                "Cargando su agenda", "", true, 0, "", emptyList(), emptyList(), 0, "Cargando…", "", false, av, null)
        }

        val nombre = r.optString("nombre", "Dr. Miguel").ifBlank { "Dr. Miguel" }
        val todas = mutableListOf<Cita>()
        r.optJSONArray("citas")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                todas.add(Cita(o.optString("d"), o.optString("h"), o.optString("f"), o.optString("p"), o.optString("m"), o.optString("e"), o.optString("a"), o.optString("x")))
            }
        }
        val hoy = hoyIso(cal)
        val manCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        val manana = hoyIso(manCal)
        val deHoy = todas.filter { it.d == hoy }.sortedBy { it.inicio() }
        val deManana = todas.filter { it.d == manana }.sortedBy { it.inicio() }
        val recs = mutableListOf<String>()
        r.optJSONArray("rec")?.let { for (i in 0 until it.length()) it.optString(i).takeIf { s -> s.isNotBlank() }?.let { s -> recs.add(s) } }
        val frase = r.optJSONArray("frase")?.let { "\"" + it.optString(0) + "\"" + (it.optString(1).let { f -> if (f.isNotBlank() && f != "Consejo") " — $f" else "" }) }
        val viejo = ahora - r.optLong("gen", ahora) > 36L * 3600000L

        val sinConfManana = deManana.count { it.e != "g" }
        val txtManana = if (deManana.isEmpty()) "Mañana no tiene citas."
            else "Mañana tiene ${deManana.size} cita" + (if (deManana.size > 1) "s" else "") + "; empieza a las ${deManana[0].h} con ${deManana[0].p}." +
                (if (sinConfManana > 0) " $sinConfManana sin confirmar." else "")
        val cdManana = if (deManana.isEmpty()) "Mañana: sin citas" else "Mañana: ${deManana[0].h} · ${deManana[0].corto()}"
        val detManana = deManana.firstOrNull()?.x ?: ""
        val mes = r.optJSONObject("mes")
        val mesN = mes?.optString("n", "") ?: ""
        val mesI = mes?.optLong("i", -1L) ?: -1L
        val mesG = mes?.optLong("g", -1L) ?: -1L

        // Cambios de estado: inicio/fin de cada cita de hoy, 21:00 (noche) y 6:00 del día siguiente
        val cambios = mutableListOf<Long>()
        deHoy.forEach { cambios.add(it.inicio()); cambios.add(it.fin()) }
        cambios.add((cal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 21); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis)
        cambios.add((manCal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 6); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis)
        val sig = cambios.filter { it > ahora + 1000 }.minOrNull()

        val atendidas = deHoy.count { it.a == "si" || (it.fin() <= ahora && it.a != "no") }
        val restantes = deHoy.filter { it.fin() > ahora && it.a != "si" }
        val prog = if (deHoy.isEmpty()) 0 else (atendidas * 100 / deHoy.size)
        val progTxt = if (deHoy.isEmpty()) "Sin citas hoy" else "$atendidas de ${deHoy.size} citas atendidas"

        val actual = restantes.firstOrNull()
        val filas = deHoy.map { c ->
            when {
                c === actual -> Fila(c.h, c.corto(), if (c.inicio() <= ahora) "Ahora" else "Siguiente", "now")
                c.a == "si" || c.fin() <= ahora -> Fila(c.h, c.corto(), "Lista", "gris")
                c.e == "g" -> Fila(c.h, c.corto(), "Confirmó", "ok")
                else -> Fila(c.h, c.corto(), "Sin confirmar", "pen")
            }
        }
        // primero lo que sigue, luego lo ya atendido
        val filasOrden = filas.filter { it.tipo != "gris" } + filas.filter { it.tipo == "gris" }

        val noche = hora >= 21 || hora < 6
        val msgs = mutableListOf<String>()
        if (noche) {
            msgs.add("Descanse, doctor. " + txtManana)
            if (frase != null) msgs.add(frase)
            msgs.addAll(recs.take(2))
            if (viejo) msgs.add("Abra DentiAgenda para que me actualice.")
            return Vista("🌙", msgs, cdManana, detManana, true, if (deHoy.isEmpty()) 0 else 100, if (deHoy.isEmpty()) "Día terminado" else "Día terminado · $atendidas de ${deHoy.size}",
                filasOrden, recs, 0, if (deManana.isEmpty()) "Mañana libre" else "Mañana ${deManana[0].h}", if (deManana.isEmpty()) "" else deManana[0].corto(), true, av, sig, mesN, mesI, mesG)
        }
        if (deHoy.isEmpty()) {
            msgs.add("$saludo, $nombre. Hoy no tiene citas.")
            msgs.add(txtManana); msgs.addAll(recs); if (frase != null) msgs.add(frase)
            if (viejo) msgs.add("Abra DentiAgenda para que me actualice.")
            return Vista("☀️", msgs, cdManana, detManana, true, 0, progTxt, emptyList(), recs, 0, "Hoy libre", "", false, av, sig, mesN, mesI, mesG)
        }
        if (actual == null) {
            msgs.add("¡Listo por hoy, $nombre! Atendió $atendidas de ${deHoy.size}.")
            msgs.add(txtManana); if (frase != null) msgs.add(frase); msgs.addAll(recs)
            if (viejo) msgs.add("Abra DentiAgenda para que me actualice.")
            return Vista("✅", msgs, cdManana, detManana, true, 100, progTxt, filasOrden, recs, 0, "¡Listo!", "${deHoy.size} citas hoy", false, av, sig, mesN, mesI, mesG)
        }
        val enCurso = actual.inicio() <= ahora
        val quedan = restantes.size
        msgs.add("$saludo, $nombre. Hoy tiene ${deHoy.size} cita" + (if (deHoy.size > 1) "s" else "") + (if (quedan < deHoy.size) "; le quedan $quedan." else "."))
        if (enCurso) {
            msgs.add("Ahora atiende a ${actual.p}: ${actual.m.ifBlank { "consulta" }}.")
            restantes.getOrNull(1)?.let { msgs.add("Después sigue ${it.p} a las ${it.h}: ${it.m.ifBlank { "consulta" }}.") }
        } else {
            msgs.add("La siguiente es ${actual.p} a las ${actual.h}: ${actual.m.ifBlank { "consulta" }}.")
        }
        if (actual.x.isNotBlank()) msgs.add("Con ${actual.p}: ${actual.x}.")
        val sinConfHoy = restantes.count { it.e != "g" }
        if (sinConfHoy > 0) msgs.add("$sinConfHoy cita" + (if (sinConfHoy > 1) "s" else "") + " de hoy sin confirmar.")
        msgs.addAll(recs)
        if (viejo) msgs.add("Abra DentiAgenda para que me actualice.")
        val emoji = if (enCurso) "🦷" else if (hora < 12) "☀️" else "🌤️"
        val cdTxt = if (enCurso) "Ahora: ${actual.corto()}" else "Próxima: ${actual.h} · ${actual.corto()}"
        return Vista(emoji, msgs, cdTxt, actual.x, false, prog, progTxt,
            filasOrden, recs, quedan, (if (enCurso) "Ahora " else "Próxima ") + actual.h, actual.corto(), false, av, sig, mesN, mesI, mesG)
    }
}
