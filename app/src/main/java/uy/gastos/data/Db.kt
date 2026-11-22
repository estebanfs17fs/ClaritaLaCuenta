package uy.gastos.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import uy.gastos.parser.Consumo

data class Cuenta(val id: Long, val nombre: String, val descripcion: String)

data class Comercio(
    val id: Long,
    val nombreOriginal: String,
    val nombrePropio: String?,
    val cuentaId: Long?,
    val cantidad: Int,
    val totalUyu: Long,
    val totalUsd: Long,
) {
    val nombreMostrado get() = nombrePropio?.takeIf { it.isNotBlank() } ?: nombreOriginal
}

data class Movimiento(
    val id: Long,
    val fecha: String,
    val tipo: String, // "egreso" o "ingreso"
    val tarjeta: String?,
    val comercioId: Long?,
    val nombreOriginal: String?,
    val nombrePropio: String?,
    val descripcion: String,
    val montoCentavos: Long,
    val moneda: String,
    val asignacion: String, // "comercio", "personal" o "cuenta"
    val cuentaId: Long?,
    val cuentaComercio: Long?,
    val cuentaEfectiva: Long?, // null = personal
    val origen: String,
) {
    val nombreMostrado: String
        get() = nombrePropio?.takeIf { it.isNotBlank() } ?: nombreOriginal ?: descripcion.ifBlank { "Sin nombre" }
}

data class CorreoNoReconocido(val fecha: String, val asunto: String, val extracto: String)

/** Avisa a la interfaz que hubo cambios en los datos. */
object Cambios {
    val version = MutableStateFlow(0)
    fun avisar() { version.value = version.value + 1 }
}

class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "gastos.db", null, 2) {

    companion object {
        @Volatile private var instancia: Db? = null
        fun get(ctx: Context): Db = instancia ?: synchronized(this) {
            instancia ?: Db(ctx.applicationContext).also { instancia = it }
        }

        private const val CUENTA_EFECTIVA = """
            CASE m.asignacion WHEN 'cuenta' THEN m.cuenta_id WHEN 'personal' THEN NULL ELSE c.cuenta_id END"""
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE cuentas (
            id INTEGER PRIMARY KEY, nombre TEXT NOT NULL UNIQUE, descripcion TEXT NOT NULL DEFAULT '')""")
        db.execSQL("""CREATE TABLE comercios (
            id INTEGER PRIMARY KEY, nombre_original TEXT NOT NULL UNIQUE, nombre_propio TEXT,
            cuenta_id INTEGER REFERENCES cuentas(id) ON DELETE SET NULL)""")
        db.execSQL("""CREATE TABLE movimientos (
            id INTEGER PRIMARY KEY, fecha TEXT NOT NULL, tipo TEXT NOT NULL, tarjeta TEXT,
            comercio_id INTEGER REFERENCES comercios(id), descripcion TEXT NOT NULL DEFAULT '',
            monto_centavos INTEGER NOT NULL, moneda TEXT NOT NULL DEFAULT 'UYU',
            asignacion TEXT NOT NULL DEFAULT 'comercio',
            cuenta_id INTEGER REFERENCES cuentas(id) ON DELETE SET NULL,
            origen TEXT NOT NULL DEFAULT 'manual', message_id TEXT UNIQUE)""")
        db.execSQL("CREATE INDEX idx_mov_fecha ON movimientos(fecha)")
        db.execSQL("""CREATE TABLE correos (
            message_id TEXT PRIMARY KEY, fecha TEXT, asunto TEXT, estado TEXT NOT NULL, extracto TEXT)""")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Versión 2: las anteriores no podían leer el cuerpo de algunos correos.
        // Se olvidan los descartados para que se vuelvan a procesar.
        if (oldVersion < 2) db.execSQL("DELETE FROM correos WHERE estado != 'ok'")
    }

    // ---------- Lectura ----------

    fun cuentas(): List<Cuenta> = readableDatabase.rawQuery(
        "SELECT id, nombre, descripcion FROM cuentas ORDER BY nombre COLLATE NOCASE", null,
    ).mapear { Cuenta(it.getLong(0), it.getString(1), it.getString(2)) }

    fun comercios(): List<Comercio> = readableDatabase.rawQuery(
        """SELECT c.id, c.nombre_original, c.nombre_propio, c.cuenta_id, COUNT(m.id),
                  COALESCE(SUM(CASE WHEN m.moneda='UYU' THEN m.monto_centavos END), 0),
                  COALESCE(SUM(CASE WHEN m.moneda='USD' THEN m.monto_centavos END), 0),
                  MAX(m.fecha) AS ultima
           FROM comercios c LEFT JOIN movimientos m ON m.comercio_id = c.id
           GROUP BY c.id
           ORDER BY (c.nombre_propio IS NOT NULL AND c.nombre_propio != ''), ultima DESC""", null,
    ).mapear {
        Comercio(it.getLong(0), it.getString(1), it.getStringOrNull(2), it.getLongOrNull(3),
            it.getInt(4), it.getLong(5), it.getLong(6))
    }

    private val SELECT_MOVIMIENTOS = """SELECT m.id, m.fecha, m.tipo, m.tarjeta, m.comercio_id, c.nombre_original,
                  c.nombre_propio, m.descripcion, m.monto_centavos, m.moneda, m.asignacion, m.cuenta_id, c.cuenta_id,
                  $CUENTA_EFECTIVA, m.origen
           FROM movimientos m LEFT JOIN comercios c ON c.id = m.comercio_id"""

    private fun leerMovimiento(it: Cursor) = Movimiento(
        id = it.getLong(0), fecha = it.getString(1), tipo = it.getString(2), tarjeta = it.getStringOrNull(3),
        comercioId = it.getLongOrNull(4), nombreOriginal = it.getStringOrNull(5),
        nombrePropio = it.getStringOrNull(6), descripcion = it.getString(7), montoCentavos = it.getLong(8),
        moneda = it.getString(9), asignacion = it.getString(10), cuentaId = it.getLongOrNull(11),
        cuentaComercio = it.getLongOrNull(12), cuentaEfectiva = it.getLongOrNull(13), origen = it.getString(14),
    )

    /** Movimientos con fecha en [desde, hasta). Fechas en formato ISO "yyyy-MM-dd". */
    fun movimientos(desde: String, hasta: String): List<Movimiento> = readableDatabase.rawQuery(
        "$SELECT_MOVIMIENTOS WHERE m.fecha >= ? AND m.fecha < ? ORDER BY m.fecha DESC, m.id DESC",
        arrayOf(desde, hasta),
    ).mapear { leerMovimiento(it) }

    fun movimiento(id: Long): Movimiento? = readableDatabase.rawQuery(
        "$SELECT_MOVIMIENTOS WHERE m.id = ?", arrayOf(id.toString()),
    ).mapear { leerMovimiento(it) }.firstOrNull()

    fun correoProcesado(messageId: String): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM correos WHERE message_id = ?", arrayOf(messageId),
    ).use { it.moveToFirst() }

    fun noReconocidos(): List<CorreoNoReconocido> = readableDatabase.rawQuery(
        "SELECT fecha, asunto, extracto FROM correos WHERE estado = 'no_reconocido' ORDER BY fecha DESC LIMIT 20",
        null,
    ).mapear { CorreoNoReconocido(it.getString(0) ?: "", it.getString(1) ?: "", it.getString(2) ?: "") }

    fun cantidadCorreosLeidos(): Int = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM correos WHERE estado = 'ok'", null,
    ).use { it.moveToFirst(); it.getInt(0) }

    // ---------- Escritura ----------

    private fun comercioId(db: SQLiteDatabase, nombre: String): Long {
        db.rawQuery("SELECT id FROM comercios WHERE nombre_original = ?", arrayOf(nombre)).use {
            if (it.moveToFirst()) return it.getLong(0)
        }
        return db.insertOrThrow("comercios", null, ContentValues().apply { put("nombre_original", nombre) })
    }

    /** Guarda el consumo y devuelve el id del movimiento creado. */
    fun guardarConsumo(messageId: String, fecha: String, asunto: String, c: Consumo): Long {
        val db = writableDatabase
        val id: Long
        db.beginTransaction()
        try {
            val comercio = comercioId(db, c.comercio)
            id = db.insertOrThrow("movimientos", null, ContentValues().apply {
                put("fecha", fecha); put("tipo", "egreso"); put("tarjeta", c.tarjeta)
                put("comercio_id", comercio); put("descripcion", c.comercioOriginal)
                put("monto_centavos", c.montoCentavos); put("moneda", c.moneda)
                put("origen", "correo"); put("message_id", messageId)
            })
            db.insertOrThrow("correos", null, ContentValues().apply {
                put("message_id", messageId); put("fecha", fecha); put("asunto", asunto); put("estado", "ok")
            })
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        Cambios.avisar()
        return id
    }

    fun guardarNoReconocido(messageId: String, fecha: String, asunto: String, texto: String) {
        writableDatabase.insertWithOnConflict("correos", null, ContentValues().apply {
            put("message_id", messageId); put("fecha", fecha); put("asunto", asunto)
            put("estado", "no_reconocido"); put("extracto", texto.take(30000))
        }, SQLiteDatabase.CONFLICT_IGNORE)
        Cambios.avisar()
    }

    fun guardarIgnorado(messageId: String, fecha: String, asunto: String) {
        writableDatabase.insertWithOnConflict("correos", null, ContentValues().apply {
            put("message_id", messageId); put("fecha", fecha); put("asunto", asunto); put("estado", "ignorado")
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    /** Vuelve a leer los correos que no se reconocieron o se ignoraron (por ejemplo, tras cambiar filtros). */
    fun olvidarNoReconocidos() {
        writableDatabase.delete("correos", "estado IN ('no_reconocido', 'ignorado')", null)
        Cambios.avisar()
    }

    /** destino: null = según comercio, 0 = personal, otro = id de cuenta. */
    fun asignarMovimiento(id: Long, destino: Long?) {
        val (asignacion, cuenta) = when (destino) {
            null -> "comercio" to null
            0L -> "personal" to null
            else -> "cuenta" to destino
        }
        writableDatabase.update("movimientos", ContentValues().apply {
            put("asignacion", asignacion); if (cuenta == null) putNull("cuenta_id") else put("cuenta_id", cuenta)
        }, "id = ?", arrayOf(id.toString()))
        Cambios.avisar()
    }

    fun agregarManual(fecha: String, tipo: String, nombre: String, descripcion: String,
                      montoCentavos: Long, moneda: String, cuentaId: Long?) {
        val db = writableDatabase
        val comercio = nombre.trim().takeIf { it.isNotEmpty() }?.let { comercioId(db, it.uppercase()) }
        db.insertOrThrow("movimientos", null, ContentValues().apply {
            put("fecha", fecha); put("tipo", tipo); put("descripcion", descripcion.trim())
            if (comercio != null) put("comercio_id", comercio)
            put("monto_centavos", montoCentavos); put("moneda", moneda)
            put("asignacion", if (cuentaId == null) "personal" else "cuenta")
            if (cuentaId != null) put("cuenta_id", cuentaId)
            put("origen", "manual")
        })
        Cambios.avisar()
    }

    fun borrarMovimiento(id: Long) {
        writableDatabase.delete("movimientos", "id = ?", arrayOf(id.toString()))
        Cambios.avisar()
    }

    fun guardarComercio(id: Long, nombrePropio: String?, cuentaId: Long?) {
        writableDatabase.update("comercios", ContentValues().apply {
            if (nombrePropio.isNullOrBlank()) putNull("nombre_propio") else put("nombre_propio", nombrePropio.trim())
            if (cuentaId == null) putNull("cuenta_id") else put("cuenta_id", cuentaId)
        }, "id = ?", arrayOf(id.toString()))
        Cambios.avisar()
    }

    /** Devuelve false si ya existe una cuenta con ese nombre. */
    fun guardarCuenta(id: Long?, nombre: String, descripcion: String): Boolean {
        val valores = ContentValues().apply { put("nombre", nombre.trim()); put("descripcion", descripcion.trim()) }
        val ok = try {
            if (id == null) writableDatabase.insertOrThrow("cuentas", null, valores)
            else writableDatabase.update("cuentas", valores, "id = ?", arrayOf(id.toString()))
            true
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            false
        }
        Cambios.avisar()
        return ok
    }

    fun borrarCuenta(id: Long) {
        val db = writableDatabase
        db.execSQL("UPDATE movimientos SET asignacion = 'comercio', cuenta_id = NULL WHERE cuenta_id = ?", arrayOf<Any?>(id))
        db.delete("cuentas", "id = ?", arrayOf(id.toString()))
        Cambios.avisar()
    }
}

private inline fun <T> Cursor.mapear(f: (Cursor) -> T): List<T> = use {
    val lista = ArrayList<T>(it.count)
    while (it.moveToNext()) lista.add(f(it))
    lista
}

private fun Cursor.getStringOrNull(i: Int): String? = if (isNull(i)) null else getString(i)
private fun Cursor.getLongOrNull(i: Int): Long? = if (isNull(i)) null else getLong(i)
