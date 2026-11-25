package com.dentilike.dentiagenda

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView

/** Recibe dentiagenda://vincular?t=CODIGO desde DentiAgenda y guarda el código del widget. */
class VincularActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.vincular)
        val titulo = findViewById<TextView>(R.id.vTitulo)
        val texto = findViewById<TextView>(R.id.vTexto)
        val agregar = findViewById<Button>(R.id.vAgregar)
        findViewById<Button>(R.id.vCerrar).setOnClickListener { finish() }

        val t = intent?.data?.getQueryParameter("t")?.trim().orEmpty()
        if (t.length < 20 || !t.matches(Regex("^[A-Za-z0-9_-]+$"))) {
            titulo.text = "No pude vincular"
            texto.text = "El enlace no trae un código válido. Vuelva a DentiAgenda → Ajustes → Widget Mikes Molares → Vincular."
            agregar.visibility = View.GONE
            return
        }
        Datos.guardarToken(this, t)
        MikesWidget.programar(this)
        MikesWidget.pedirDescarga(this)
        MikesWidget.redibujarTodos(this)
        titulo.text = "¡Listo, doctor!"

        val mgr = AppWidgetManager.getInstance(this)
        val yaHay = mgr.getAppWidgetIds(ComponentName(this, MikesWidget::class.java)).isNotEmpty()
        val sePuedeFijar = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mgr.isRequestPinAppWidgetSupported
        texto.text = if (yaHay) "Mikes Molares ya está conectado con su agenda. En unos segundos verá sus citas en el widget."
            else "Mikes Molares ya está conectado. Ahora póngalo en su pantalla de inicio" +
                (if (sePuedeFijar) " con el botón de abajo." else ": mantenga presionado un espacio vacío → Widgets → DentiAgenda.")
        if (!yaHay && sePuedeFijar) {
            agregar.setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mgr.requestPinAppWidget(ComponentName(this, MikesWidget::class.java), null, null)
                }
            }
        } else agregar.visibility = View.GONE
    }
}
