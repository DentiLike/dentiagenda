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
        WorkManager.getInstance(ctx).cancelUniqueWork(TRABAJO_PERIODICO)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        when (intent.action) {
            ACCION_SIGUIENTE -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                val lay = Datos.layoutDe(ctx, id)
                if (id != -1 && lay != 0 && lay != R.layout.widget_chico) {
                    val v = RemoteViews(ctx.packageName, lay)
                    v.showNext(R.id.flipper)
                    AppWidgetManager.getInstance(ctx).partiallyUpdateAppWidget(id, v)
                } else redibujarTodos(ctx)
            }
            ACCION_REDIBUJAR -> redibujarTodos(ctx)
            ACCION_DINERO -> {
                val hasta = System.currentTimeMillis() + 15000L
                Datos.mostrarDinero(ctx, hasta)
                redibujarTodos(ctx)
                val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.set(AlarmManager.RTC, hasta + 500L, aviso(ctx, ACCION_REDIBUJAR, 0, 9998))
            }
            ACCION_ACTUALIZAR -> { redibujarTodos(ctx); pedirDescarga(ctx) }
            ACCION_MES -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                if (id != -1) { Datos.alternarMes(ctx, id); dibujar(ctx, AppWidgetManager.getInstance(ctx), id) }
            }
        }
    }

    companion object {
        const val ACCION_SIGUIENTE = "com.dentilike.dentiagenda.SIGUIENTE"
        const val ACCION_REDIBUJAR = "com.dentilike.dentiagenda.REDIBUJAR"
        const val ACCION_ACTUALIZAR = "com.dentilike.dentiagenda.ACTUALIZAR"
        const val ACCION_DINERO = "com.dentilike.dentiagenda.DINERO"
        const val ACCION_MES = "com.dentilike.dentiagenda.MES"

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
        private fun pintarSemana(ctx: Context, v: RemoteViews, vista: Vista, id: Int) {
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
                v.setInt(SEM_CAJA[k], "setBackgroundResource", if (esHoy) R.drawable.dia_hoy else fondoDia(n))
                v.setTextColor(SEM_L[k], if (esHoy) 0xFFD6E4F5.toInt() else 0xFF5A6B85.toInt())
                v.setTextColor(SEM_N[k], if (esHoy) 0xFFFFFFFF.toInt() else if (pasado) 0xFF8A97AB.toInt() else 0xFF1E2B3C.toInt())
                v.setTextColor(SEM_C[k], if (esHoy) 0xFFFFFFFF.toInt() else if (n > 0) 0xFF124578.toInt() else 0xFFB4BFCE.toInt())
                v.setOnClickPendingIntent(SEM_CAJA[k], abrir(ctx, "$BASE?ir=dia&f=$iso", 200000 + id * 64 + k))
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
                v.setOnClickPendingIntent(MES_DIAS[k], abrir(ctx, "$BASE?ir=dia&f=$iso", 300000 + id * 64 + k))
            }
            v.setTextViewText(R.id.secAgenda, "${MESES[mes]} $anio")
            return filas
        }

        private fun dinero(n: Long): String = "$" + String.format(java.util.Locale.US, "%,d", n)
        private const val TRABAJO_PERIODICO = "mikes_periodico"
        private const val TRABAJO_YA = "mikes_ya"
        private const val BASE = "https://dentilike.github.io/dentiagenda/index.html"

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

        private fun abrir(ctx: Context, url: String, codigo: Int): PendingIntent {
            val i = Intent(ctx, LauncherActivity::class.java).apply {
                data = Uri.parse(url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            return PendingIntent.getActivity(ctx, codigo, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun aviso(ctx: Context, accion: String, id: Int, codigo: Int): PendingIntent {
            val i = Intent(ctx, MikesWidget::class.java).apply {
                action = accion
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
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
            vista.mensajes.forEach { m ->
                val r = RemoteViews(ctx.packageName, R.layout.mensaje)
                r.setTextViewText(R.id.texto, m)
                v.addView(R.id.flipper, r)
            }
            v.setOnClickPendingIntent(R.id.avatarBox, aviso(ctx, ACCION_SIGUIENTE, id, id * 10 + 3))
            v.setOnClickPendingIntent(R.id.flipper, aviso(ctx, ACCION_SIGUIENTE, id, id * 10 + 3))

            // Siguiente cita y qué le toca (arco actual y siguiente, o el tratamiento pendiente)
            v.setTextViewText(R.id.cdTxt, vista.cdTexto)
            v.setInt(R.id.cdBox, "setBackgroundResource", if (vista.cdGris) R.drawable.cd_gris else R.drawable.cd_azul)
            if (vista.cdDetalle.isNotBlank()) {
                v.setTextViewText(R.id.cdDet, vista.cdDetalle)
                v.setViewVisibility(R.id.cdDet, View.VISIBLE)
            } else v.setViewVisibility(R.id.cdDet, View.GONE)

            // Dinero del mes: oculto; al tocarlo se muestra 15 segundos
            if (vista.mesIngresos >= 0) {
                val ver = System.currentTimeMillis() < Datos.verDineroHasta(ctx)
                val mesTxt = vista.mesNombre.replaceFirstChar { it.uppercase() }
                val txt = if (ver) "$mesTxt: ingresos ${dinero(vista.mesIngresos)} · gastos ${dinero(vista.mesGastos)}"
                    else "$mesTxt: ingresos •••• · gastos ••••   (toca para ver)"
                v.setTextViewText(R.id.dinTxt, txt)
                v.setViewVisibility(R.id.dinTxt, View.VISIBLE)
                v.setOnClickPendingIntent(R.id.dinTxt, aviso(ctx, ACCION_DINERO, id, id * 10 + 9))
            } else v.setViewVisibility(R.id.dinTxt, View.GONE)
            v.setOnClickPendingIntent(R.id.cdBox, abrir(ctx, if (vinculado) "$BASE?ir=hoy" else "$BASE?ir=widget", id * 10 + 4))
            v.setTextViewText(R.id.barTxt, vista.progresoTxt)
            v.setProgressBar(R.id.bar, 100, vista.progreso, false)

            val opA = mgr.getAppWidgetOptions(id)
            val altoW = opA.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 }
                ?: opA.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
            val verMes = lay == R.layout.widget_grande && vinculado && Datos.verMes(ctx, id)
            val conSemana = vinculado && !verMes && (lay == R.layout.widget_grande || altoW >= 250)
            v.setViewVisibility(R.id.semana, if (conSemana) View.VISIBLE else View.GONE)
            if (conSemana) pintarSemana(ctx, v, vista, id)

            if (lay == R.layout.widget_grande) {
                val op = mgr.getAppWidgetOptions(id)
                val alto = op.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 }
                    ?: op.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 360)
                v.setTextViewText(R.id.btnMes, if (verMes) "Ver hoy" else "Ver mes")
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
                    v.setOnClickPendingIntent(idsFila[k], abrir(ctx, "$BASE?ir=hoy", id * 10 + 5))
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
