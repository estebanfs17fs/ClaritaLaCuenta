package uy.gastos.sync

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import uy.gastos.R
import uy.gastos.data.Db
import uy.gastos.ui.MainActivity
import uy.gastos.ui.dinero

/** Avisos al usuario: gastos nuevos, correos que no se pudieron leer y fallas de conexión. */
object Notificaciones {
    const val CANAL_GASTOS = "gastos"
    const val CANAL_ALERTAS = "alertas"
    const val CANAL_SERVICIO = "servicio"

    const val ID_SERVICIO = 1
    private const val ID_RESUMEN = 2
    private const val ID_NO_RECONOCIDO = 3
    private const val ID_ERROR = 4
    private const val GRUPO = "gastos-nuevos"

    const val EXTRA_MOVIMIENTO = "movimiento"
    const val EXTRA_PANTALLA = "pantalla"

    fun crearCanales(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(listOf(
            NotificationChannel(CANAL_GASTOS, "Gastos nuevos", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Un aviso por cada consumo detectado en el correo" },
            NotificationChannel(CANAL_ALERTAS, "Problemas al leer el correo", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Correos que no se pudieron interpretar o fallas de conexión" },
            NotificationChannel(CANAL_SERVICIO, "Revisión en segundo plano", NotificationManager.IMPORTANCE_MIN)
                .apply { description = "Aviso fijo mientras la app revisa el correo"; setShowBadge(false) },
        ))
    }

    fun puedeNotificar(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun abrir(ctx: Context, codigo: Int, configurar: Intent.() -> Unit): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            configurar()
        }
        return PendingIntent.getActivity(ctx, codigo, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @SuppressLint("MissingPermission")
    private fun mostrar(ctx: Context, id: Int, n: NotificationCompat.Builder) {
        if (!puedeNotificar(ctx)) return
        try { NotificationManagerCompat.from(ctx).notify(id, n.build()) } catch (_: SecurityException) {}
    }

    fun avisarHallazgos(ctx: Context, h: LectorCorreo.Hallazgos) {
        crearCanales(ctx)
        val db = Db.get(ctx)
        val movs = h.nuevos.mapNotNull { db.movimiento(it) }
        if (movs.size in 1..3) {
            val cuentas = db.cuentas().associateBy { it.id }
            movs.forEach { m ->
                val monto = dinero(m.montoCentavos, m.moneda)
                val destino = m.cuentaEfectiva?.let { cuentas[it]?.nombre }
                val texto = if (destino != null) "Se cargó en $destino. Tocá para revisarlo."
                    else "Quedó como personal. Tocá para asignarlo a una cuenta."
                mostrar(ctx, (100 + m.id % 100000).toInt(), base(ctx, CANAL_GASTOS)
                    .setContentTitle("$monto en ${m.nombreMostrado}")
                    .setContentText(texto)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(
                        "$texto\nTarjeta ${m.tarjeta ?: "-"} · ${m.nombreOriginal ?: m.descripcion}"))
                    .setGroup(GRUPO)
                    .setContentIntent(abrir(ctx, m.id.toInt()) { putExtra(EXTRA_MOVIMIENTO, m.id) }))
            }
        } else if (movs.size > 3) {
            val total = movs.groupBy { it.moneda }.map { (mon, l) -> dinero(l.sumOf { it.montoCentavos }, mon) }
            mostrar(ctx, ID_RESUMEN, base(ctx, CANAL_GASTOS)
                .setContentTitle("${movs.size} gastos nuevos")
                .setContentText("Total ${total.joinToString(" + ")}. Tocá para revisarlos.")
                .setStyle(NotificationCompat.InboxStyle().also { s ->
                    movs.take(6).forEach { s.addLine("${dinero(it.montoCentavos, it.moneda)} · ${it.nombreMostrado}") }
                })
                .setContentIntent(abrir(ctx, ID_RESUMEN) { putExtra(EXTRA_PANTALLA, "actividad") }))
        }
        if (h.noReconocidos.isNotEmpty()) {
            val n = h.noReconocidos.size
            mostrar(ctx, ID_NO_RECONOCIDO, base(ctx, CANAL_ALERTAS)
                .setContentTitle(if (n == 1) "No pude leer un correo del banco" else "No pude leer $n correos del banco")
                .setContentText("Tocá para ver el correo y cargar el gasto a mano.")
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    "Coincide con el filtro pero no tiene el formato esperado: ${h.noReconocidos.joinToString(", ")}. " +
                        "Tocá para ver el correo y cargar el gasto a mano."))
                .setContentIntent(abrir(ctx, ID_NO_RECONOCIDO) { putExtra(EXTRA_PANTALLA, "ajustes") }))
        }
    }

    fun errorDeConexion(ctx: Context, detalle: String) {
        crearCanales(ctx)
        mostrar(ctx, ID_ERROR, base(ctx, CANAL_ALERTAS)
            .setContentTitle("No puedo revisar el correo")
            .setContentText(detalle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$detalle\nVoy a seguir intentando. Si no se arregla solo, revisá la contraseña en Ajustes."))
            .setContentIntent(abrir(ctx, ID_ERROR) { putExtra(EXTRA_PANTALLA, "ajustes") }))
    }

    fun quitarError(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID_ERROR)

    fun base(ctx: Context, canal: String): NotificationCompat.Builder =
        NotificationCompat.Builder(ctx, canal)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setColor(0xFF1F5F55.toInt())
            .setAutoCancel(true)
            .setPriority(if (canal == CANAL_SERVICIO) NotificationCompat.PRIORITY_MIN else NotificationCompat.PRIORITY_HIGH)
}
