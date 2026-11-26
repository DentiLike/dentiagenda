package com.dentilike.dentiagenda

import org.json.JSONObject
import java.util.Calendar

/** Una cita tal como la manda DentiAgenda: fecha, hora de inicio y fin, nombre del paciente, motivo, estado y asistencia. */
data class Cita(val d: String, val h: String, val f: String, val p: String, val m: String, val e: String, val a: String, val x: String = "", val pv: Boolean = false) {
    fun inicio(): Long = epoch(d, h)
    fun fin(): Long { val x = epoch(d, f); return if (x > inicio()) x else inicio() + 30 * 60000L }
    fun corto(): String = if (m.isNotBlank()) "$p · $m" else p
    /** Hora de inicio en formato de 12 horas, igual que en DentiAgenda: 4:00 PM */
    fun h12(): String = hora12(h)

    companion object {
        fun hora12(hora: String): String = try {
            val p = hora.split(":").map { it.trim().toInt() }
            val h = p[0] % 12
            "${if (h == 0) 12 else h}:${String.format("%02d", p.getOrElse(1) { 0 })} ${if (p[0] >= 12) "PM" else "AM"}"
        } catch (e: Exception) { hora }

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

/** Un mensaje de Mikes, con un botón opcional que abre DentiAgenda en el lugar indicado. */
data class Msg(val texto: String, val boton: String = "", val url: String = "")

data class Fila(val hora: String, val texto: String, val tag: String, val tipo: String)

/** Todo lo que el widget necesita dibujar en este momento. */
data class Vista(
    val emoji: String,
    val mensajes: List<Msg>,
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
    val cal: Map<String, Int> = emptyMap()   // citas por día (AAAA-MM-DD → número) para el mini calendario
)

object Estado {

    /** Motivo y, si hay, lo que le toca (arco o tratamiento), cada uno en su renglón. */
    private fun detalle(c: Cita?): String = if (c == null) "" else listOf(c.m, c.x).filter { it.isNotBlank() }.joinToString("\n")

    private fun enc(t: String): String = java.net.URLEncoder.encode(t, "UTF-8")
    private fun h12(ms: Long): String { val c = Calendar.getInstance().apply { timeInMillis = ms }
        return Cita.hora12("${c.get(Calendar.HOUR_OF_DAY)}:${c.get(Calendar.MINUTE)}") }
    private fun lista(n: List<String>): String = when (n.size) {
        0 -> ""; 1 -> n[0]; 2 -> "${n[0]} y ${n[1]}"
        3 -> "${n[0]}, ${n[1]} y ${n[2]}"
        else -> "${n[0]}, ${n[1]}, ${n[2]} y ${n.size - 3} más" }
    private fun plural(n: Int, uno: String, varios: String) = if (n == 1) "1 $uno" else "$n $varios"

    /** Cada recordatorio de DentiAgenda con el botón que lo resuelve. */
    private fun recMsg(t: String): Msg = when {
        t.contains("solicitud") -> Msg(t + ".", "Ver solicitudes", "?ir=solicitudes")
        t.contains("trazado") -> Msg(t + ".", "Ver trazados", "?ir=trazados")
        t.startsWith("Surtir") -> Msg(t + ".", "Mi consultorio", "?ir=consultorio")
        else -> Msg(t + ".", "Mi consultorio", "?ir=consultorio")
    }

    fun hoyIso(c: Calendar): String =
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
            return Vista("👋", listOf(Msg("Hola, soy Mikes Molares. Para mostrarle su agenda, vincúleme desde DentiAgenda.", "Vincular", "?ir=widget")),
                "Toque aquí para abrir DentiAgenda", "", true, 0, "", emptyList(), emptyList(), 0, "Sin vincular", "Abra DentiAgenda", false, av, null)
        }
        if (r == null) {
            return Vista("⏳", listOf(Msg("Conectando con DentiAgenda… Si tarda, ábrala una vez para que me mande su agenda.", "Abrir", "?ir=hoy")),
                "Cargando su agenda", "", true, 0, "", emptyList(), emptyList(), 0, "Cargando…", "", false, av, null)
        }

        val nombre = r.optString("nombre", "Dr. Miguel").ifBlank { "Dr. Miguel" }
        val todas = mutableListOf<Cita>()
        r.optJSONArray("citas")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                todas.add(Cita(o.optString("d"), o.optString("h"), o.optString("f"), o.optString("p"), o.optString("m"), o.optString("e"), o.optString("a"), o.optString("x"), o.optInt("pv", 0) == 1))
            }
        }
        val calMap = mutableMapOf<String, Int>()
        r.optJSONObject("cal")?.let { o -> o.keys().forEach { k -> calMap[k] = o.optInt(k, 0) } }
        if (calMap.isEmpty()) todas.forEach { calMap[it.d] = (calMap[it.d] ?: 0) + 1 }
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
            else "Mañana tiene ${deManana.size} cita" + (if (deManana.size > 1) "s" else "") + "; empieza a las ${deManana[0].h12()} con ${deManana[0].p}." +
                (if (sinConfManana > 0) " $sinConfManana sin confirmar." else "")
        val cdManana = if (deManana.isEmpty()) "Mañana: sin citas" else "Mañana ${deManana[0].h12()} · ${deManana[0].p}"
        val detManana = detalle(deManana.firstOrNull())

        // Cambios de estado: inicio/fin de cada cita de hoy, 21:00 (noche) y 6:00 del día siguiente
        val cambios = mutableListOf<Long>()
        deHoy.forEach { cambios.add(it.inicio() - 30 * 60000L); cambios.add(it.inicio()); cambios.add(it.fin()) }
        cambios.add((cal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 14); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis)
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
                c === actual -> Fila(c.h12(), c.corto(), if (c.inicio() <= ahora) "Ahora" else "Siguiente", "now")
                c.a == "si" || c.fin() <= ahora -> Fila(c.h12(), c.corto(), "Lista", "gris")
                c.e == "g" -> Fila(c.h12(), c.corto(), "Confirmó", "ok")
                else -> Fila(c.h12(), c.corto(), "Sin confirmar", "pen")
            }
        }
        // primero lo que sigue, luego lo ya atendido
        val filasOrden = filas.filter { it.tipo != "gris" } + filas.filter { it.tipo == "gris" }

        val noche = hora >= 21 || hora < 6
        val msgs = mutableListOf<Msg>()
        val fraseMsg = frase?.let { Msg(it) }
        val faltaron = deHoy.count { it.a == "no" }
        val primerasHoy = deHoy.count { it.pv }
        val sinConfHoyTotal = deHoy.count { it.e != "g" && it.fin() > ahora && it.a != "si" }

        // Pacientes de hoy que ya se fueron y no tienen próxima cita
        val sinProx = mutableListOf<String>()
        r.optJSONArray("sp")?.let { arr ->
            for (i in 0 until arr.length()) {
                val x = arr.optJSONArray(i) ?: continue
                val fin = Cita.epoch(hoy, x.optString(1, "00:00"))
                if (noche || fin <= ahora) sinProx.add(x.optString(0))
            }
        }
        val msgSinProx = if (sinProx.isEmpty()) null else Msg(
            (if (sinProx.size == 1) "${sinProx[0]} se fue sin próxima cita." else "${sinProx.size} pacientes de hoy se fueron sin próxima cita: ${lista(sinProx)}."),
            "Agendar", if (sinProx.size == 1) "?ir=paciente&n=${enc(sinProx[0])}" else "?ir=hoy")

        // Citas de mañana sin confirmar
        val manSinConf = deManana.filter { it.e != "g" }
        val msgManana = if (manSinConf.isEmpty()) null else Msg(
            "Mañana hay " + plural(manSinConf.size, "cita sin confirmar", "citas sin confirmar") + ": " +
                lista(manSinConf.map { "${it.p} ${it.h12()}" }) + ".", "Confirmar", "?ir=dia&f=$manana")

        val sol = r.optInt("sol", 0)

        // Cuántas citas en la mañana del día (resumen de arranque)
        fun resumenDia(): Msg {
            if (deHoy.isEmpty()) return Msg("$saludo, $nombre. Hoy no tiene citas. " + txtManana)
            val partes = mutableListOf<String>()
            if (primerasHoy > 0) partes.add(plural(primerasHoy, "primera vez", "primeras veces"))
            val sc = deHoy.count { it.e != "g" }
            if (sc > 0) partes.add(plural(sc, "sin confirmar", "sin confirmar"))
            return Msg("$saludo, $nombre. Hoy tiene " + plural(deHoy.size, "cita", "citas") +
                (if (partes.isNotEmpty()) " (" + partes.joinToString(", ") + ")" else "") +
                ". Empieza a las ${deHoy[0].h12()} con ${deHoy[0].p}.", "Ver agenda", "?ir=hoy")
        }

        if (noche) {
            msgs.add(Msg(
                (if (deHoy.isEmpty()) "Descanse, doctor. Hoy no hubo citas. " else "Descanse, doctor. Hoy atendió $atendidas de ${deHoy.size}" +
                    (if (faltaron > 0) " y " + plural(faltaron, "faltó", "faltaron") else "") + ". ") + txtManana))
            fraseMsg?.let { msgs.add(it) }
            msgSinProx?.let { msgs.add(it) }
            msgManana?.let { msgs.add(it) }
            recs.take(2).forEach { msgs.add(recMsg(it)) }
            if (viejo) msgs.add(Msg("Abra DentiAgenda para que me actualice.", "Abrir", "?ir=hoy"))
            return Vista("🌙", msgs, cdManana, detManana, true, if (deHoy.isEmpty()) 0 else 100, if (deHoy.isEmpty()) "Día terminado" else "Día terminado · $atendidas de ${deHoy.size}",
                filasOrden, recs, 0, if (deManana.isEmpty()) "Mañana libre" else "Mañana ${deManana[0].h12()}", if (deManana.isEmpty()) "" else deManana[0].p, true, av, sig, cal = calMap)
        }

        val inicioDia = if (deHoy.isEmpty()) hora < 12 else ahora < deHoy[0].inicio() - 30 * 60000L

        // 1) Prepara la cita: la que está en curso o empieza en 30 minutos o menos
        if (actual != null && actual.inicio() - ahora <= 30 * 60000L) {
            val enCursoYa = actual.inicio() <= ahora
            if (actual.pv) msgs.add(Msg(
                (if (enCursoYa) "Ahora atiende a ${actual.p}, primera vez." else "A las ${actual.h12()} viene ${actual.p}, primera vez.") +
                    " Tenga lista la historia clínica, el consentimiento y el aviso de privacidad.", "Abrir primera cita", "?ir=pc&n=${enc(actual.p)}"))
            else msgs.add(Msg(
                (if (enCursoYa) "Ahora atiende a ${actual.p}" else "Sigue ${actual.p} a las ${actual.h12()}") +
                    (if (actual.m.isNotBlank()) ": ${actual.m}." else ".") + (if (actual.x.isNotBlank()) " Toca: ${actual.x}." else ""),
                "Ver expediente", "?ir=paciente&n=${enc(actual.p)}"))
        }
        // 2) Arranque del día: resumen y frase del día
        if (inicioDia) { msgs.add(resumenDia()); fraseMsg?.let { msgs.add(it) } }
        // 3) Se fueron sin próxima cita
        msgSinProx?.let { msgs.add(it) }
        // 4) Por la tarde: confirmar las de mañana
        if (hora >= 14) msgManana?.let { msgs.add(it) }
        // 5) La siguiente cita (si todavía falta más de media hora)
        if (actual != null && actual.inicio() - ahora > 30 * 60000L && !inicioDia) {
            msgs.add(Msg("La siguiente es ${actual.p} a las ${actual.h12()}" + (if (actual.m.isNotBlank()) ": ${actual.m}." else ".") +
                (if (actual.pv) " Es primera vez." else if (actual.x.isNotBlank()) " Toca: ${actual.x}." else ""),
                if (actual.pv) "Abrir primera cita" else "Ver expediente", if (actual.pv) "?ir=pc&n=${enc(actual.p)}" else "?ir=paciente&n=${enc(actual.p)}"))
        }
        if (sinConfHoyTotal > 0 && !inicioDia) msgs.add(Msg(plural(sinConfHoyTotal, "cita de hoy sigue sin confirmar.", "citas de hoy siguen sin confirmar."), "Ver agenda", "?ir=hoy"))
        // 6) Huecos libres de 45 minutos o más entre las citas que faltan
        val pend = deHoy.filter { it.a != "no" }
        var hueco: Pair<Long, Long>? = null
        for (k in 0 until pend.size - 1) {
            val ini = maxOf(pend[k].fin(), ahora); val fin = pend[k + 1].inicio()
            if (fin > ahora && fin - ini >= 45 * 60000L) { hueco = ini to fin; break }
        }
        hueco?.let { (a, b) ->
            msgs.add(Msg("Tiene libre de ${h12(a)} a ${h12(b)}." + (if (sol > 0) " Hay " + plural(sol, "solicitud de cita esperando.", "solicitudes de cita esperando.") else ""),
                if (sol > 0) "Ver solicitudes" else "", if (sol > 0) "?ir=solicitudes" else ""))
        }
        // 7) Fin de la jornada
        if (deHoy.isNotEmpty() && actual == null) {
            msgs.add(0, Msg("¡Listo por hoy, $nombre! Atendió $atendidas de ${deHoy.size}" + (if (faltaron > 0) "; " + plural(faltaron, "faltó", "faltaron") else "") + ". " + txtManana))
        }
        // 8) Recordatorios de DentiAgenda (estudios, firmas, trazados, surtir, solicitudes)
        recs.filter { !(hueco != null && sol > 0 && it.contains("solicitud")) }.forEach { msgs.add(recMsg(it)) }
        if (msgs.isEmpty()) msgs.add(resumenDia())
        if (viejo) msgs.add(Msg("Abra DentiAgenda para que me actualice.", "Abrir", "?ir=hoy"))

        if (deHoy.isEmpty()) {
            return Vista("☀️", msgs, cdManana, detManana, true, 0, progTxt, emptyList(), recs, 0, "Hoy libre", "", false, av, sig, cal = calMap)
        }
        if (actual == null) {
            return Vista("✅", msgs, cdManana, detManana, true, 100, progTxt, filasOrden, recs, 0, "¡Listo!", "${deHoy.size} citas hoy", false, av, sig, cal = calMap)
        }
        val enCurso = actual.inicio() <= ahora
        val quedan = restantes.size
        val emoji = if (enCurso) "🦷" else if (hora < 12) "☀️" else "🌤️"
        val cdTxt = if (enCurso) "Ahora · ${actual.p}" else "Próxima ${actual.h12()} · ${actual.p}"
        return Vista(emoji, msgs, cdTxt, detalle(actual), false, prog, progTxt,
            filasOrden, recs, quedan, (if (enCurso) "Ahora " else "Próxima ") + actual.h12(), actual.p, false, av, sig, cal = calMap)
    }
}
