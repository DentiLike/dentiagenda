package com.dentilike.dentiagenda

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.work.WorkManager
import java.util.Calendar

/** Widget de calendario mensual: días con citas sombreados; al tocar un día muestra abajo quién viene. */
class CalendarioWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        MikesWidget.programar(ctx)
        ids.forEach { dibujar(ctx, mgr, it) }
        MikesWidget.pedirDescarga(ctx)
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opciones: Bundle) {
        dibujar(ctx, mgr, id)
    }

    override fun onEnabled(ctx: Context) { MikesWidget.programar(ctx); MikesWidget.pedirDescarga(ctx) }

    override fun onDisabled(ctx: Context) {
        val quedan = AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, MikesWidget::class.java)).isNotEmpty()
        if (!quedan) WorkManager.getInstance(ctx).cancelUniqueWork(MikesWidget.TRABAJO_PERIODICO)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val mgr = AppWidgetManager.getInstance(ctx)
        when (intent.action) {
            ACCION_DIA -> if (id != -1) {
                Datos.pGuardar(ctx, "cal_sel_$id", intent.getStringExtra("f") ?: "")
                dibujar(ctx, mgr, id)
            }
            ACCION_MES -> if (id != -1) {
                val d = intent.getIntExtra("d", 0)
                if (d == 0) { Datos.pGuardar(ctx, "cal_off_$id", 0); Datos.pGuardar(ctx, "cal_sel_$id", "") }
                else Datos.pGuardar(ctx, "cal_off_$id", (Datos.pInt(ctx, "cal_off_$id", 0) + d).coerceIn(0, 1))
                dibujar(ctx, mgr, id)
            }
            ACCION_REDIBUJAR -> redibujarTodos(ctx)
        }
    }

    companion object {
        const val ACCION_DIA = "com.dentilike.dentiagenda.CAL_DIA"
        const val ACCION_MES = "com.dentilike.dentiagenda.CAL_MES"
        const val ACCION_REDIBUJAR = "com.dentilike.dentiagenda.CAL_REDIBUJAR"

        private val MESES = arrayOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
        private val DIAS = arrayOf("domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado")
        private val SEM = intArrayOf(R.id.cw0, R.id.cw1, R.id.cw2, R.id.cw3, R.id.cw4, R.id.cw5, R.id.cw6)
        private val FILAS = intArrayOf(R.id.cf0, R.id.cf1, R.id.cf2, R.id.cf3, R.id.cf4, R.id.cf5)
        private val LISTA = intArrayOf(R.id.cl0, R.id.cl1, R.id.cl2, R.id.cl3, R.id.cl4, R.id.cl5, R.id.cl6, R.id.cl7)
        private val CELDAS = intArrayOf(
            R.id.cd0, R.id.cd1, R.id.cd2, R.id.cd3, R.id.cd4, R.id.cd5, R.id.cd6,
            R.id.cd7, R.id.cd8, R.id.cd9, R.id.cd10, R.id.cd11, R.id.cd12, R.id.cd13,
            R.id.cd14, R.id.cd15, R.id.cd16, R.id.cd17, R.id.cd18, R.id.cd19, R.id.cd20,
            R.id.cd21, R.id.cd22, R.id.cd23, R.id.cd24, R.id.cd25, R.id.cd26, R.id.cd27,
            R.id.cd28, R.id.cd29, R.id.cd30, R.id.cd31, R.id.cd32, R.id.cd33, R.id.cd34,
            R.id.cd35, R.id.cd36, R.id.cd37, R.id.cd38, R.id.cd39, R.id.cd40, R.id.cd41)

        fun redibujarTodos(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            mgr.getAppWidgetIds(ComponentName(ctx, CalendarioWidget::class.java)).forEach { dibujar(ctx, mgr, it) }
        }

        private fun aviso(ctx: Context, accion: String, id: Int, codigo: Int, extra: (Intent) -> Unit = {}): PendingIntent {
            val i = Intent(ctx, CalendarioWidget::class.java).apply {
                action = accion
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                extra(this)
            }
            return PendingIntent.getBroadcast(ctx, codigo, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun iso(a: Int, m: Int, d: Int) = String.format("%04d-%02d-%02d", a, m + 1, d)

        fun dibujar(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val v = RemoteViews(ctx.packageName, R.layout.widget_calendario)
            val transp = Datos.pInt(ctx, "cal_tr_$id", 0).coerceIn(0, 100)
            val claro = Datos.pBool(ctx, "cal_cl_$id")          // letra clara sobre fondo oscuro
            val off = Datos.pInt(ctx, "cal_off_$id", 0)
            val vinculado = Datos.token(ctx) != null
            val r = Datos.resumen(ctx)

            // Colores según el modo
            val cTxt = if (claro) 0xFFFFFFFF.toInt() else 0xFF1E2B3C.toInt()
            val cSub = if (claro) 0xFFE3EAF2.toInt() else 0xFF5A6B85.toInt()
            val cPas = if (claro) 0xFFB7C3D1.toInt() else 0xFF8A97AB.toInt()
            val cCita = if (claro) 0xFFFFFFFF.toInt() else 0xFF124578.toInt()
            val cPend = if (claro) 0xFFFAC775.toInt() else 0xFF975A16.toInt()

            v.setImageViewResource(R.id.calFondo, if (claro) R.drawable.cal_fondo_osc else R.drawable.cal_fondo_claro)
            v.setInt(R.id.calFondo, "setImageAlpha", (255 * (100 - transp) / 100))
            v.setInt(R.id.calLista, "setBackgroundResource", if (claro) R.drawable.cal_lista_osc else R.drawable.cal_lista_claro)
            listOf(R.id.calPrev, R.id.calNext, R.id.calTit).forEach { v.setTextColor(it, cTxt) }
            v.setTextColor(R.id.calSub, cSub)
            SEM.forEach { v.setTextColor(it, cSub) }
            v.setTextColor(R.id.calDiaTit, cTxt)
            v.setTextColor(R.id.calMas, cSub)

            // Citas del mes: fecha → [[hora, nombre, confirmó]]
            val mc = r?.optJSONObject("mc")
            val calN = r?.optJSONObject("cal")
            fun citasDe(f: String): List<Triple<String, String, Boolean>> {
                val arr = mc?.optJSONArray(f) ?: return emptyList()
                return (0 until arr.length()).mapNotNull { i -> arr.optJSONArray(i)?.let { Triple(it.optString(0), it.optString(1), it.optString(2) == "g") } }
            }
            fun cuantas(f: String): Int = mc?.optJSONArray(f)?.length() ?: calN?.optInt(f, 0) ?: 0

            val hoyC = Calendar.getInstance()
            val hoy = Estado.hoyIso(hoyC)
            val c = Calendar.getInstance().apply { add(Calendar.MONTH, off); set(Calendar.DAY_OF_MONTH, 1) }
            val anio = c.get(Calendar.YEAR); val mes = c.get(Calendar.MONTH)
            val hueco = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
            val dias = c.getActualMaximum(Calendar.DAY_OF_MONTH)
            val nFilas = (hueco + dias + 6) / 7
            var totalMes = 0
            for (d in 1..dias) totalMes += cuantas(iso(anio, mes, d))

            v.setTextViewText(R.id.calTit, MESES[mes].replaceFirstChar { it.uppercase() } + " $anio")
            v.setTextViewText(R.id.calSub, if (!vinculado) "Sin vincular" else if (r == null) "Cargando…" else
                (if (totalMes == 1) "1 cita" else "$totalMes citas") + (if (off != 0) " · toque aquí para volver a hoy" else ""))
            v.setOnClickPendingIntent(R.id.calPrev, aviso(ctx, ACCION_MES, id, 600000 + id * 4) { it.putExtra("d", -1) })
            v.setOnClickPendingIntent(R.id.calNext, aviso(ctx, ACCION_MES, id, 600000 + id * 4 + 1) { it.putExtra("d", 1) })
            v.setOnClickPendingIntent(R.id.calTitBox, aviso(ctx, ACCION_MES, id, 600000 + id * 4 + 2) { it.putExtra("d", 0) })
            v.setViewVisibility(R.id.calPrev, if (off > 0) View.VISIBLE else View.INVISIBLE)
            v.setViewVisibility(R.id.calNext, if (off < 1) View.VISIBLE else View.INVISIBLE)

            var sel = Datos.pStr(ctx, "cal_sel_$id")
            if (!sel.startsWith(String.format("%04d-%02d", anio, mes + 1))) sel = if (off == 0) hoy else iso(anio, mes, 1)

            // Tamaños según el alto del widget: la cuadrícula llena el espacio y la lista usa hasta ~40 %
            val op = mgr.getAppWidgetOptions(id)
            val alto = op.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 }
                ?: op.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 320)
            val listaSel = citasDe(sel)
            val fLista = when { alto >= 560 -> 15f; alto >= 420 -> 13.5f; else -> 12f }
            val renglon = fLista * 1.45f
            val maxRen = (((alto * 0.42f) - renglon - 20f) / renglon).toInt().coerceIn(0, 8)
            val mostrar = if (listaSel.size > maxRen) (maxRen - 1).coerceAtLeast(0) else listaSel.size
            val listaH = 20f + renglon * (1 + mostrar + (if (listaSel.size > mostrar) 1 else 0))
            val celda = (alto - 24 - 40 - 20 - 8 - listaH) / nFilas - 2f
            val esc = (celda / 30f).coerceIn(1f, 2f)
            val fDia = (12f * esc).coerceAtMost(22f)
            v.setTextViewTextSize(R.id.calTit, TypedValue.COMPLEX_UNIT_SP, (15f * esc).coerceAtMost(22f))
            v.setTextViewTextSize(R.id.calSub, TypedValue.COMPLEX_UNIT_SP, (10f * esc).coerceAtMost(13f))
            SEM.forEach { v.setTextViewTextSize(it, TypedValue.COMPLEX_UNIT_SP, (10f * esc).coerceAtMost(14f)) }
            v.setTextViewTextSize(R.id.calDiaTit, TypedValue.COMPLEX_UNIT_SP, fLista)
            LISTA.forEach { v.setTextViewTextSize(it, TypedValue.COMPLEX_UNIT_SP, fLista) }
            v.setTextViewTextSize(R.id.calMas, TypedValue.COMPLEX_UNIT_SP, fLista - 1f)

            for (f in 0 until 6) v.setViewVisibility(FILAS[f], if (f < nFilas) View.VISIBLE else View.GONE)
            for (k in 0 until 42) {
                val d = k - hueco + 1
                if (d < 1 || d > dias) {
                    v.setTextViewText(CELDAS[k], "")
                    v.setInt(CELDAS[k], "setBackgroundResource", R.drawable.dia_nada)
                    continue
                }
                val f = iso(anio, mes, d)
                val n = cuantas(f)
                val esHoy = f == hoy; val esSel = f == sel
                val txt = SpannableString(if (n > 0) "$d\n$n" else "$d")
                if (n > 0) {
                    txt.setSpan(StyleSpan(Typeface.BOLD), 0, d.toString().length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    txt.setSpan(RelativeSizeSpan(0.72f), d.toString().length, txt.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                v.setTextViewText(CELDAS[k], txt)
                v.setTextViewTextSize(CELDAS[k], TypedValue.COMPLEX_UNIT_SP, fDia)
                val fondo = when {
                    esHoy -> if (claro) R.drawable.dia_hoy_cl else R.drawable.dia_hoy
                    esSel && n >= 6 -> if (claro) R.drawable.dia_sel_lleno_cl else R.drawable.dia_sel_lleno
                    esSel && n > 0 -> if (claro) R.drawable.dia_sel_cita_cl else R.drawable.dia_sel_cita
                    esSel -> if (claro) R.drawable.dia_sel_cl else R.drawable.dia_sel
                    n >= 6 -> if (claro) R.drawable.dia_lleno_cl else R.drawable.dia_lleno
                    n > 0 -> if (claro) R.drawable.dia_cita_cl else R.drawable.dia_cita
                    else -> R.drawable.dia_nada
                }
                v.setInt(CELDAS[k], "setBackgroundResource", fondo)
                v.setTextColor(CELDAS[k], when {
                    esHoy -> if (claro) 0xFF14202D.toInt() else 0xFFFFFFFF.toInt()
                    n > 0 -> cCita
                    f < hoy -> cPas
                    else -> cTxt
                })
                v.setOnClickPendingIntent(CELDAS[k], aviso(ctx, ACCION_DIA, id, 500000 + id * 64 + k) { it.putExtra("f", f) })
            }

            // Lista del día elegido
            v.setViewVisibility(R.id.calLista, if (celda >= 26f || mostrar > 0) View.VISIBLE else View.GONE)
            if (!vinculado) {
                v.setTextViewText(R.id.calDiaTit, "Vincule el widget desde DentiAgenda → Ajustes")
                LISTA.forEach { v.setViewVisibility(it, View.GONE) }
                v.setViewVisibility(R.id.calMas, View.GONE)
                v.setOnClickPendingIntent(R.id.calLista, MikesWidget.abrir(ctx, MikesWidget.BASE + "?ir=widget", 700000 + id))
            } else {
                val ps = sel.split("-").map { it.toInt() }
                val cs = Calendar.getInstance().apply { set(ps[0], ps[1] - 1, ps[2]) }
                val lista = listaSel
                val n = if (lista.isNotEmpty()) lista.size else cuantas(sel)
                val nomDia = DIAS[cs.get(Calendar.DAY_OF_WEEK) - 1]
                v.setTextViewText(R.id.calDiaTit, (if (sel == hoy) "Hoy, " else nomDia.replaceFirstChar { it.uppercase() } + " ") +
                    (if (sel == hoy) "$nomDia " else "") + "${ps[2]} de ${MESES[ps[1] - 1]} · " +
                    (if (n == 0) "sin citas" else if (n == 1) "1 cita" else "$n citas"))
                for (k in 0 until 8) {
                    val x = lista.getOrNull(k)
                    if (x == null || k >= mostrar) { v.setViewVisibility(LISTA[k], View.GONE); continue }
                    v.setViewVisibility(LISTA[k], View.VISIBLE)
                    v.setTextViewText(LISTA[k], Cita.hora12(x.first) + " · " + x.second + (if (x.third) "" else " · sin confirmar"))
                    v.setTextColor(LISTA[k], if (x.third) cTxt else cPend)
                }
                if (lista.size > mostrar) { v.setViewVisibility(R.id.calMas, View.VISIBLE); v.setTextViewText(R.id.calMas, "y ${lista.size - mostrar} más · toque para abrir el día") }
                else v.setViewVisibility(R.id.calMas, View.GONE)
                v.setOnClickPendingIntent(R.id.calLista, MikesWidget.abrir(ctx, MikesWidget.BASE + "?ir=dia&f=$sel", 700000 + id))
            }
            mgr.updateAppWidget(id, v)

            // Redibujar al cambiar de día
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val man = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 30) }
            am.set(AlarmManager.RTC, man.timeInMillis, aviso(ctx, ACCION_REDIBUJAR, 0, 9997))
        }
    }
}
