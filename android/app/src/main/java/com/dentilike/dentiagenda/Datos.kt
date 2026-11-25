package com.dentilike.dentiagenda

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Guarda el código de vinculación y descarga el resumen que DentiAgenda publica en Firestore
 * (documento widget/{código}). El documento solo trae lo necesario para el widget: horas, iniciales y motivos.
 */
object Datos {
    private const val PREFS = "mikes_molares"
    private const val PROYECTO = "misonrisa-citas"
    private const val API_KEY = "AIzaSyC4Kws78y5K3-1IuBibxCOo-a11ccuNYUE"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun token(ctx: Context): String? = prefs(ctx).getString("token", null)

    fun guardarToken(ctx: Context, t: String) {
        prefs(ctx).edit().putString("token", t).remove("json").apply()
    }

    fun resumen(ctx: Context): JSONObject? {
        val s = prefs(ctx).getString("json", null) ?: return null
        return try { JSONObject(s) } catch (e: Exception) { null }
    }

    fun ultimaDescarga(ctx: Context): Long = prefs(ctx).getLong("descargado", 0L)

    /** Descarga el resumen. Devuelve true si llegó algo nuevo y válido. */
    fun descargar(ctx: Context): Boolean {
        val t = token(ctx) ?: return false
        var con: HttpURLConnection? = null
        return try {
            val url = URL("https://firestore.googleapis.com/v1/projects/$PROYECTO/databases/(default)/documents/widget/$t?key=$API_KEY")
            con = url.openConnection() as HttpURLConnection
            con.connectTimeout = 15000
            con.readTimeout = 15000
            con.requestMethod = "GET"
            if (con.responseCode != 200) return false
            val cuerpo = con.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(cuerpo).getJSONObject("fields").getJSONObject("json").getString("stringValue")
            JSONObject(json) // valida
            prefs(ctx).edit().putString("json", json).putLong("descargado", System.currentTimeMillis()).apply()
            true
        } catch (e: Exception) {
            false
        } finally {
            con?.disconnect()
        }
    }

    fun mostrarDinero(ctx: Context, hasta: Long) { prefs(ctx).edit().putLong("dinero_hasta", hasta).apply() }
    fun verDineroHasta(ctx: Context): Long = prefs(ctx).getLong("dinero_hasta", 0L)

    fun verMes(ctx: Context, id: Int): Boolean = prefs(ctx).getBoolean("mes_$id", false)
    fun alternarMes(ctx: Context, id: Int) { prefs(ctx).edit().putBoolean("mes_$id", !verMes(ctx, id)).apply() }

    fun guardarLayout(ctx: Context, id: Int, layout: Int) { prefs(ctx).edit().putInt("layout_$id", layout).apply() }
    fun layoutDe(ctx: Context, id: Int): Int = prefs(ctx).getInt("layout_$id", 0)
}
