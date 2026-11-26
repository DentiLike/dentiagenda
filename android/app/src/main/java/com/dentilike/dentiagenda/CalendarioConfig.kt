package com.dentilike.dentiagenda

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView

/** Se abre al poner el widget de calendario (y en "Configurar"): transparencia y color de letra. */
class CalendarioConfig : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setContentView(R.layout.config_calendario)

        val barra = findViewById<SeekBar>(R.id.cfgTr)
        val txt = findViewById<TextView>(R.id.cfgTrTxt)
        val osc = findViewById<RadioButton>(R.id.cfgOsc)
        val cla = findViewById<RadioButton>(R.id.cfgCla)
        barra.progress = Datos.pInt(this, "cal_tr_$id", 0) / 5
        txt.text = "${barra.progress * 5}%"
        barra.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, deUsuario: Boolean) { txt.text = "${p * 5}%" }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        if (Datos.pBool(this, "cal_cl_$id")) cla.isChecked = true else osc.isChecked = true

        findViewById<Button>(R.id.cfgListo).setOnClickListener {
            Datos.pGuardar(this, "cal_tr_$id", barra.progress * 5)
            Datos.pGuardar(this, "cal_cl_$id", cla.isChecked)
            CalendarioWidget.dibujar(this, AppWidgetManager.getInstance(this), id)
            MikesWidget.programar(this)
            MikesWidget.pedirDescarga(this)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            finish()
        }
    }
}
