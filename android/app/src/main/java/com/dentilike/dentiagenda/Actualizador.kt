package com.dentilike.dentiagenda

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/** Cada 15 minutos (y al vincular o agregar el widget) descarga el resumen y redibuja. */
class Actualizador(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        Datos.descargar(applicationContext)
        MikesWidget.redibujarTodos(applicationContext)
        CalendarioWidget.redibujarTodos(applicationContext)
        return Result.success()
    }
}
