package com.dentilike.dentiagenda

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.androidbrowserhelper.trusted.LauncherActivity
import java.util.concurrent.TimeUnit

class MikesWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        programar(ctx)
        ids.forEach { dibujar(ctx, mgr, it) }
        pedirDescarga(ctx)
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opciones: Bundle) {
        dibujar(ctx, mgr, id)
    }

    override fun onEnabled(ctx: Context) { programar(ctx); pedirDescarga(ctx) }

    override fun onDisabled(ctx: Context) {
        val quedan = AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, CalendarioWidget::class.java)).isNotEmpty()
        if (!quedan) WorkManager.getInstance(ctx).cancelUniqueWork(TRABAJO_PERIODICO)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        when (intent.action) {
            ACCION_SIGUIENTE -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                val lay = Datos.layoutDe(ctx, id)
                if (id != -1 && tipActivo(ctx, id)) {           // estaba mostrando un tip: regresa a los avisos
                    Datos.pGuardar(ctx, "tip_hasta_$id", "0")
                    dibujar(ctx, AppWidgetManager.getInstance(ctx), id)
                } else if (id != -1 && lay != 0 && lay != R.layout.widget_chico) {
                    val v = RemoteViews(ctx.packageName, lay)
                    v.showNext(R.id.flipper)
                    AppWidgetManager.getInstance(ctx).partiallyUpdateAppWidget(id, v)
                } else redibujarTodos(ctx)
            }
            ACCION_REDIBUJAR -> redibujarTodos(ctx)
            ACCION_ACTUALIZAR -> { redibujarTodos(ctx); pedirDescarga(ctx) }
            ACCION_MES -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                if (id != -1) {
                    if (diaSel(ctx, id).isNotEmpty()) elegirDia(ctx, id, "") else Datos.alternarMes(ctx, id)
                    dibujar(ctx, AppWidgetManager.getInstance(ctx), id)
                }
            }
            ACCION_DIA -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                val f = intent.getStringExtra("f") ?: ""
                if (id != -1) {
                    val hoy = Estado.hoyIso(java.util.Calendar.getInstance())
                    elegirDia(ctx, id, if (f == hoy || f == diaSel(ctx, id)) "" else f)
                    if (Datos.verMes(ctx, id)) Datos.alternarMes(ctx, id)
                    dibujar(ctx, AppWidgetManager.getInstance(ctx), id)
                }
            }
            ACCION_TIP -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                if (id != -1) {
                    val hoy = Estado.hoyIso(java.util.Calendar.getInstance())
                    val n = if (Datos.pStr(ctx, "tip_dia_$id") == hoy) Datos.pInt(ctx, "tip_n_$id", -1) + 1 else 0
                    Datos.pGuardar(ctx, "tip_n_$id", n); Datos.pGuardar(ctx, "tip_dia_$id", hoy)
                    val hasta = System.currentTimeMillis() + 3 * 60000L
                    Datos.pGuardar(ctx, "tip_hasta_$id", hasta.toString())
                    dibujar(ctx, AppWidgetManager.getInstance(ctx), id)
                    val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    am.set(AlarmManager.RTC, hasta + 1000L, aviso(ctx, ACCION_REDIBUJAR, 0, 800000 + id))
                }
            }
        }
    }

    companion object {
        const val ACCION_SIGUIENTE = "com.dentilike.dentiagenda.SIGUIENTE"
        const val ACCION_REDIBUJAR = "com.dentilike.dentiagenda.REDIBUJAR"
        const val ACCION_ACTUALIZAR = "com.dentilike.dentiagenda.ACTUALIZAR"
        const val ACCION_MES = "com.dentilike.dentiagenda.MES"
        const val ACCION_DIA = "com.dentilike.dentiagenda.DIA"
        const val ACCION_TIP = "com.dentilike.dentiagenda.TIP"

        /** Día elegido en la semana (solo vale el mismo día en que se eligió). */
        fun diaSel(ctx: Context, id: Int): String {
            val hoy = Estado.hoyIso(java.util.Calendar.getInstance())
            return if (Datos.pStr(ctx, "sel_set_$id") == hoy) Datos.pStr(ctx, "sel_$id") else ""
        }
        fun elegirDia(ctx: Context, id: Int, f: String) {
            Datos.pGuardar(ctx, "sel_$id", f); Datos.pGuardar(ctx, "sel_set_$id", Estado.hoyIso(java.util.Calendar.getInstance()))
        }
        fun tipActivo(ctx: Context, id: Int): Boolean = System.currentTimeMillis() < (Datos.pStr(ctx, "tip_hasta_$id").toLongOrNull() ?: 0L)

        private var consejos: List<Pair<String, String>>? = null
        private fun cargarConsejos(ctx: Context): List<Pair<String, String>> {
            consejos?.let { return it }
            val lista = try {
                val arr = org.json.JSONArray(ctx.resources.openRawResource(R.raw.consejos).bufferedReader(Charsets.UTF_8).readText())
                (0 until arr.length()).map { arr.getJSONObject(it).let { o -> o.optString("e") to o.optString("t") } }
            } catch (e: Exception) { emptyList() }
            consejos = lista
            return lista
        }
        /** El primer toque del día es la frase del día; después, tips de ortodoncia, ATM y tratamientos intercalados con frases. */
        private fun tipDe(ctx: Context, id: Int, frase: String?): Pair<String, String> {
            val n = Datos.pInt(ctx, "tip_n_$id", 0)
            if (n == 0 && !frase.isNullOrBlank()) return "Frase del día" to frase
            val l = cargarConsejos(ctx)
            if (l.isEmpty()) return "Frase del día" to (frase ?: "")
            val dia = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
            return l[((dia * 7 + n) % l.size + l.size) % l.size]
        }

        private val MESES = arrayOf("ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO", "JULIO", "AGOSTO", "SEPTIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE")
        private val SEM_CAJA = intArrayOf(R.id.sem0, R.id.sem1, R.id.sem2, R.id.sem3, R.id.sem4, R.id.sem5, R.id.sem6)
        private val SEM_L = intArrayOf(R.id.semL0, R.id.semL1, R.id.semL2, R.id.semL3, R.id.semL4, R.id.semL5, R.id.semL6)
        private val SEM_N = intArrayOf(R.id.semN0, R.id.semN1, R.id.semN2, R.id.semN3, R.id.semN4, R.id.semN5, R.id.semN6)
        private val SEM_C = intArrayOf(R.id.semC0, R.id.semC1, R.id.semC2, R.id.semC3, R.id.semC4, R.id.semC5, R.id.semC6)
        private val MES_FILAS = intArrayOf(R.id.mf0, R.id.mf1, R.id.mf2, R.id.mf3, R.id.mf4, R.id.mf5)
        private val MES_DIAS = intArrayOf(
            R.id.md0, R.id.md1, R.id.md2, R.id.md3, R.id.md4, R.id.md5, R.id.md6,
            R.id.md7, R.id.md8, R.id.md9, R.id.md10, R.id.md11, R.id.md12, R.id.md13,
            R.id.md14, R.id.md15, R.id.md16, R.id.md17, R.id.md18, R.id.md19, R.id.md20,
            R.id.md21, R.id.md22, R.id.md23, R.id.md24, R.id.md25, R.id.md26, R.id.md27,
            R.id.md28, R.id.md29, R.id.md30, R.id.md31, R.id.md32, R.id.md33, R.id.md34,
            R.id.md35, R.id.md36, R.id.md37, R.id.md38, R.id.md39, R.id.md40, R.id.md41)

        private fun fondoDia(n: Int): Int = when { n >= 6 -> R.drawable.dia_lleno; n > 0 -> R.drawable.dia_cita; else -> R.drawable.dia_nada }

        /** Semana actual (lunes a domingo): número de día y cuántas citas hay; tocar un día abre ese día en DentiAgenda. */
        private fun pintarSemana(ctx: Context, v: RemoteViews, vista: Vista, id: Int, conLista: Boolean, sel: String) {
            val c = java.util.Calendar.getInstance()
            val hoy = Estado.hoyIso(c)
            c.add(java.util.Calendar.DAY_OF_MONTH, -((c.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7))
            for (k in 0 until 7) {
                val iso = Estado.hoyIso(c)
                val n = vista.cal[iso] ?: 0
                val esHoy = iso == hoy
                val pasado = iso < hoy
                v.setTextViewText(SEM_N[k], c.get(java.util.Calendar.DAY_OF_MONTH).toString())
                v.setTextViewText(SEM_C[k], if (n > 0) (if (n == 1) "1 cita" else "$n citas") else "—")
                v.setInt(SEM_CAJA[k], "setBackgroundResource", when {
                    esHoy -> R.drawable.dia_hoy
                    iso == sel && n >= 6 -> R.drawable.dia_sel_lleno
                    iso == sel && n > 0 -> R.drawable.dia_sel_cita
                    iso == sel -> R.drawable.dia_sel
                    else -> fondoDia(n)
                })
                v.setTextColor(SEM_L[k], if (esHoy) 0xFFD6E4F5.toInt() else 0xFF5A6B85.toInt())
                v.setTextColor(SEM_N[k], if (esHoy) 0xFFFFFFFF.toInt() else if (pasado) 0xFF8A97AB.toInt() else 0xFF1E2B3C.toInt())
                v.setTextColor(SEM_C[k], if (esHoy) 0xFFFFFFFF.toInt() else if (n > 0) 0xFF124578.toInt() else 0xFFB4BFCE.toInt())
                v.setOnClickPendingIntent(SEM_CAJA[k], if (conLista) aviso(ctx, ACCION_DIA, id, 200000 + id * 64 + k, iso)
                    else abrir(ctx, "$BASE?ir=dia&f=$iso", 200000 + id * 64 + k))
                c.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
        }

        /** Mes actual en cuadrícula; los días con citas van sombreados (más oscuro si son 6 o más). */
        private fun pintarMes(ctx: Context, v: RemoteViews, vista: Vista, id: Int): Int {
            val c = java.util.Calendar.getInstance()
            val hoy = Estado.hoyIso(c)
            val anio = c.get(java.util.Calendar.YEAR); val mes = c.get(java.util.Calendar.MONTH)
            c.set(java.util.Calendar.DAY_OF_MONTH, 1)
            val hueco = (c.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
            val dias = c.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
            val filas = (hueco + dias + 6) / 7
            for (r in 0 until 6) v.setViewVisibility(MES_FILAS[r], if (r < filas) View.VISIBLE else View.GONE)
            for (k in 0 until 42) {
                val d = k - hueco + 1
                if (d < 1 || d > dias) {
                    v.setTextViewText(MES_DIAS[k], "")
                    v.setInt(MES_DIAS[k], "setBackgroundResource", R.drawable.dia_nada)
                    continue
                }
                val iso = String.format("%04d-%02d-%02d", anio, mes + 1, d)
                val n = vista.cal[iso] ?: 0
                val esHoy = iso == hoy
                v.setTextViewText(MES_DIAS[k], d.toString())
                v.setInt(MES_DIAS[k], "setBackgroundResource", if (esHoy) R.drawable.dia_hoy else fondoDia(n))
                v.setTextColor(MES_DIAS[k], when {
                    esHoy -> 0xFFFFFFFF.toInt()
                    n > 0 -> 0xFF124578.toInt()
                    iso < hoy -> 0xFF8A97AB.toInt()
                    else -> 0xFF1E2B3C.toInt()
                })
                v.setOnClickPendingIntent(MES_DIAS[k], aviso(ctx, ACCION_DIA, id, 300000 + id * 64 + k, iso))
            }
            v.setTextViewText(R.id.secAgenda, "${MESES[mes]} $anio")
            return filas
        }

        const val TRABAJO_PERIODICO = "mikes_periodico"
        private const val TRABAJO_YA = "mikes_ya"
        const val BASE = "https://dentilike.github.io/dentiagenda/index.html"

        fun programar(ctx: Context) {
            val red = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val p = PeriodicWorkRequestBuilder<Actualizador>(15, TimeUnit.MINUTES).setConstraints(red).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(TRABAJO_PERIODICO, ExistingPeriodicWorkPolicy.KEEP, p)
        }

        fun pedirDescarga(ctx: Context) {
            val red = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val w = OneTimeWorkRequestBuilder<Actualizador>().setConstraints(red).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(TRABAJO_YA, ExistingWorkPolicy.REPLACE, w)
        }

        fun redibujarTodos(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            mgr.getAppWidgetIds(ComponentName(ctx, MikesWidget::class.java)).forEach { dibujar(ctx, mgr, it) }
        }

        fun abrir(ctx: Context, url: String, codigo: Int): PendingIntent {
            val i = Intent(ctx, LauncherActivity::class.java).apply {
                data = Uri.parse(url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            return PendingIntent.getActivity(ctx, codigo, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun aviso(ctx: Context, accion: String, id: Int, codigo: Int, dia: String? = null): PendingIntent {
            val i = Intent(ctx, MikesWidget::class.java).apply {
                action = accion
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                if (dia != null) putExtra("f", dia)
            }
            return PendingIntent.getBroadcast(ctx, codigo, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun elegirLayout(mgr: AppWidgetManager, id: Int): Int {
            val op = mgr.getAppWidgetOptions(id)
            val alto = op.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 }
                ?: op.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
            val ancho = op.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            return when {
                ancho < 200 -> R.layout.widget_chico
                alto >= 300 -> R.layout.widget_grande
                else -> R.layout.widget_mediano
            }
        }

        fun dibujar(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val lay = elegirLayout(mgr, id)
            Datos.guardarLayout(ctx, id, lay)
            val vinculado = Datos.token(ctx) != null
            val vista = Estado.calcular(Datos.resumen(ctx), vinculado)
            val v = RemoteViews(ctx.packageName, lay)

            v.setImageViewResource(R.id.avatar, vista.avatar)
            v.setInt(R.id.avatar, "setImageAlpha", if (vista.noche) 170 else 255)
            v.setOnClickPendingIntent(R.id.raiz, abrir(ctx, "$BASE?ir=hoy", id * 10 + 1))

            if (lay == R.layout.widget_chico) {
                v.setTextViewText(R.id.chProx, vista.chicoProx)
                v.setTextViewText(R.id.chNom, vista.chicoNombre)
                if (vista.citasRestantes > 0) {
                    v.setViewVisibility(R.id.insignia, View.VISIBLE)
                    v.setTextViewText(R.id.insignia, vista.citasRestantes.toString())
                } else v.setViewVisibility(R.id.insignia, View.GONE)
                if (!vinculado) v.setOnClickPendingIntent(R.id.raiz, abrir(ctx, "$BASE?ir=widget", id * 10 + 2))
                mgr.updateAppWidget(id, v)
                return
            }

            v.setTextViewText(R.id.emoji, vista.emoji)
            v.removeAllViews(R.id.flipper)
            val enTip = vinculado && tipActivo(ctx, id)
            val total = vista.mensajes.size
            if (enTip) {
                val fr = Datos.resumen(ctx)?.optJSONArray("frase")?.let { a ->
                    "\u201C" + a.optString(0) + "\u201D" + a.optString(1).let { x -> if (x.isNotBlank() && x != "Consejo") " — $x" else "" } }
                val (etq, txt) = tipDe(ctx, id, fr)
                val r = RemoteViews(ctx.packageName, R.layout.mensaje)
                r.setInt(R.id.texto, "setMaxLines", 7)
                r.setTextViewText(R.id.texto, txt)
                r.setTextViewText(R.id.msgCont, "$etq · toque a Mikes para otro")
                r.setViewVisibility(R.id.msgBtn, View.VISIBLE)
                r.setTextViewText(R.id.msgBtn, "Volver")
                r.setOnClickPendingIntent(R.id.msgBtn, aviso(ctx, ACCION_SIGUIENTE, id, id * 10 + 3))
                v.addView(R.id.flipper, r)
                v.setTextViewText(R.id.emoji, "💡")
            } else vista.mensajes.forEachIndexed { k, m ->
                val r = RemoteViews(ctx.packageName, R.layout.mensaje)
                r.setTextViewText(R.id.texto, m.texto)
                r.setTextViewText(R.id.msgCont, if (total > 1) "${k + 1} de $total · toque aquí para seguir" else "Toque a Mikes para un tip")
                if (m.boton.isNotBlank() && m.url.isNotBlank()) {
                    r.setViewVisibility(R.id.msgBtn, View.VISIBLE)
                    r.setTextViewText(R.id.msgBtn, m.boton)
                    r.setOnClickPendingIntent(R.id.msgBtn, abrir(ctx, BASE + m.url, 400000 + id * 32 + k))
                } else r.setViewVisibility(R.id.msgBtn, View.GONE)
                v.addView(R.id.flipper, r)
            }
            v.setOnClickPendingIntent(R.id.avatarBox, if (vinculado) aviso(ctx, ACCION_TIP, id, id * 10 + 9) else aviso(ctx, ACCION_SIGUIENTE, id, id * 10 + 3))
            v.setOnClickPendingIntent(R.id.flipper, aviso(ctx, ACCION_SIGUIENTE, id, id * 10 + 3))

            // Siguiente cita y qué le toca (arco actual y siguiente, o el tratamiento pendiente)
            v.setTextViewText(R.id.cdTxt, vista.cdTexto)
            v.setInt(R.id.cdBox, "setBackgroundResource", if (vista.cdGris) R.drawable.cd_gris else R.drawable.cd_azul)
            if (vista.cdDetalle.isNotBlank()) {
                v.setTextViewText(R.id.cdDet, vista.cdDetalle)
                v.setViewVisibility(R.id.cdDet, View.VISIBLE)
            } else v.setViewVisibility(R.id.cdDet, View.GONE)

            v.setOnClickPendingIntent(R.id.cdBox, abrir(ctx, if (vinculado) "$BASE?ir=hoy" else "$BASE?ir=widget", id * 10 + 4))
            v.setTextViewText(R.id.barTxt, vista.progresoTxt)
            v.setProgressBar(R.id.bar, 100, vista.progreso, false)

            val opA = mgr.getAppWidgetOptions(id)
            val altoW = opA.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 }
                ?: opA.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
            val verMes = lay == R.layout.widget_grande && vinculado && Datos.verMes(ctx, id)
            val conSemana = vinculado && !verMes && (lay == R.layout.widget_grande || altoW >= 250)
            v.setViewVisibility(R.id.semana, if (conSemana) View.VISIBLE else View.GONE)
            val sel = if (lay == R.layout.widget_grande) diaSel(ctx, id) else ""
            if (conSemana) pintarSemana(ctx, v, vista, id, lay == R.layout.widget_grande, sel)

            if (lay == R.layout.widget_grande) {
                val op = mgr.getAppWidgetOptions(id)
                val alto = op.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 }
                    ?: op.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 360)
                v.setTextViewText(R.id.btnMes, if (sel.isNotEmpty()) "Volver a hoy" else if (verMes) "Ver hoy" else "Ver mes")
                v.setOnClickPendingIntent(R.id.btnMes, aviso(ctx, ACCION_MES, id, id * 10 + 2))
                v.setViewVisibility(R.id.btnMes, if (vinculado) View.VISIBLE else View.GONE)
                v.setViewVisibility(R.id.mesBox, if (verMes) View.VISIBLE else View.GONE)
                val recs: List<String>
                val filas: List<Fila>
                if (verMes) {
                    val nf = pintarMes(ctx, v, vista, id)
                    val libre = alto - 290 - 20 - nf * 24
                    recs = if (libre >= 60) vista.recordatorios.take(((libre - 20) / 20).coerceIn(1, 3)) else emptyList()
                    filas = emptyList()
                } else if (sel.isNotEmpty()) {
                    // Día elegido en la semana: su lista de pacientes
                    val arr = Datos.resumen(ctx)?.optJSONObject("mc")?.optJSONArray(sel)
                    val hoy = Estado.hoyIso(java.util.Calendar.getInstance())
                    val lista = (0 until (arr?.length() ?: 0)).mapNotNull { i -> arr?.optJSONArray(i) }.map { x ->
                        val nom = x.optString(1); val mot = x.optString(3)
                        Fila(Cita.hora12(x.optString(0)), if (mot.isNotBlank()) "$nom · $mot" else nom,
                            if (sel < hoy) "Pasada" else if (x.optString(2) == "g") "Confirmó" else "Sin confirmar",
                            if (sel < hoy) "gris" else if (x.optString(2) == "g") "ok" else "pen")
                    }
                    val n = if (arr != null) lista.size else (vista.cal[sel] ?: 0)
                    val ps = sel.split("-").map { it.toInt() }
                    val cs = java.util.Calendar.getInstance().apply { set(ps[0], ps[1] - 1, ps[2]) }
                    val nomDia = arrayOf("DOMINGO", "LUNES", "MARTES", "MIÉRCOLES", "JUEVES", "VIERNES", "SÁBADO")[cs.get(java.util.Calendar.DAY_OF_WEEK) - 1]
                    val maxRec = if (alto >= 520) 2 else if (alto >= 470) 1 else 0
                    recs = vista.recordatorios.take(maxRec)
                    val maxFilas = ((alto - 340 - (if (recs.isEmpty()) 0 else 20 + recs.size * 20)) / 30).coerceIn(1, 5)
                    filas = lista.take(maxFilas)
                    v.setTextViewText(R.id.secAgenda, "$nomDia ${ps[2]} · " + (if (n == 0) "SIN CITAS" else if (n == 1) "1 CITA" else "$n CITAS") +
                        (if (lista.size > filas.size) " · +${lista.size - filas.size}" else ""))
                } else {
                    v.setTextViewText(R.id.secAgenda, if (vista.filas.isEmpty()) "HOY SIN CITAS" else "AGENDA DE HOY")
                    val maxRec = if (alto >= 470) 3 else 2
                    recs = vista.recordatorios.take(maxRec)
                    val maxFilas = ((alto - 340 - recs.size * 20) / 30).coerceIn(1, 5)
                    filas = vista.filas.take(maxFilas)
                }
                val idsFila = intArrayOf(R.id.fila1, R.id.fila2, R.id.fila3, R.id.fila4, R.id.fila5)
                val idsHora = intArrayOf(R.id.hora1, R.id.hora2, R.id.hora3, R.id.hora4, R.id.hora5)
                val idsTxt = intArrayOf(R.id.txt1, R.id.txt2, R.id.txt3, R.id.txt4, R.id.txt5)
                val idsTag = intArrayOf(R.id.tag1, R.id.tag2, R.id.tag3, R.id.tag4, R.id.tag5)
                for (k in 0 until 5) {
                    val f = filas.getOrNull(k)
                    if (f == null) { v.setViewVisibility(idsFila[k], View.GONE); continue }
                    v.setViewVisibility(idsFila[k], View.VISIBLE)
                    v.setTextViewText(idsHora[k], f.hora)
                    v.setTextViewText(idsTxt[k], f.texto)
                    v.setTextViewText(idsTag[k], f.tag)
                    val (fondo, color) = when (f.tipo) {
                        "now" -> R.drawable.tag_now to 0xFFFFFFFF.toInt()
                        "ok" -> R.drawable.tag_ok to 0xFF22543D.toInt()
                        "pen" -> R.drawable.tag_pen to 0xFF975A16.toInt()
                        else -> R.drawable.tag_gris to 0xFF4A5568.toInt()
                    }
                    v.setInt(idsTag[k], "setBackgroundResource", fondo)
                    v.setTextColor(idsTag[k], color)
                    v.setOnClickPendingIntent(idsFila[k], abrir(ctx, if (sel.isNotEmpty()) "$BASE?ir=dia&f=$sel" else "$BASE?ir=hoy", id * 10 + 5))
                }
                v.setViewVisibility(R.id.secAgenda, if (filas.isEmpty() && !verMes && !vinculado) View.GONE else View.VISIBLE)
                val idsRec = intArrayOf(R.id.rec1, R.id.rec2, R.id.rec3)
                for (k in 0 until 3) {
                    val t = recs.getOrNull(k)
                    if (t == null) v.setViewVisibility(idsRec[k], View.GONE)
                    else { v.setViewVisibility(idsRec[k], View.VISIBLE); v.setTextViewText(idsRec[k], "• $t") }
                }
                v.setViewVisibility(R.id.secRec, if (recs.isEmpty()) View.GONE else View.VISIBLE)
                v.setOnClickPendingIntent(R.id.btnCita, abrir(ctx, "$BASE?ir=nueva", id * 10 + 6))
                v.setOnClickPendingIntent(R.id.btnAgenda, abrir(ctx, "$BASE?ir=hoy", id * 10 + 7))
                v.setOnClickPendingIntent(R.id.btnPend, abrir(ctx, "$BASE?ir=consultorio", id * 10 + 8))
            }
            mgr.updateAppWidget(id, v)

            // que se vuelva a dibujar solo cuando cambie algo (empieza o termina una cita, llega la noche…)
            vista.siguienteCambio?.let { t ->
                val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pi = aviso(ctx, ACCION_REDIBUJAR, 0, 9999)
                am.setAndAllowWhileIdle(AlarmManager.RTC, t + 2000, pi)
            }
        }
    }
}
