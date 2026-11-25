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
        }
    }

    companion object {
        const val ACCION_SIGUIENTE = "com.dentilike.dentiagenda.SIGUIENTE"
        const val ACCION_REDIBUJAR = "com.dentilike.dentiagenda.REDIBUJAR"
        const val ACCION_ACTUALIZAR = "com.dentilike.dentiagenda.ACTUALIZAR"
        const val ACCION_DINERO = "com.dentilike.dentiagenda.DINERO"

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

            if (lay == R.layout.widget_grande) {
                val op = mgr.getAppWidgetOptions(id)
                val alto = op.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 }
                    ?: op.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 360)
                val maxRec = if (alto >= 420) 3 else 2
                val recs = vista.recordatorios.take(maxRec)
                val maxFilas = ((alto - 290 - recs.size * 20) / 30).coerceIn(1, 5)
                val filas = vista.filas.take(maxFilas)
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
                v.setViewVisibility(R.id.secAgenda, if (filas.isEmpty()) View.GONE else View.VISIBLE)
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
