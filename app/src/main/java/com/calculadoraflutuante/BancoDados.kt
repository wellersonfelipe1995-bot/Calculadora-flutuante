package com.calculadoraflutuante

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class ResumoDia(
    val data: LocalDate,
    val bruto: Double,
    val corridas: Int,
    val km: Double,
    val kmPorLitro: Double,
    val precoLitro: Double
) {
    val gasto: Double get() = if (kmPorLitro > 0) km / kmPorLitro * precoLitro else 0.0
    val liquido: Double get() = bruto - gasto
}

class BancoDados(context: Context) : SQLiteOpenHelper(context, "calculadora.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE corridas (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                data TEXT NOT NULL,
                hora TEXT NOT NULL,
                valor REAL NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX idx_corridas_data ON corridas(data)")
        db.execSQL(
            """CREATE TABLE combustivel (
                data TEXT PRIMARY KEY,
                km REAL NOT NULL,
                km_por_litro REAL NOT NULL,
                preco_litro REAL NOT NULL
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun salvarCorrida(valor: Double) {
        writableDatabase.insert("corridas", null, ContentValues().apply {
            put("data", hoje())
            put("hora", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
            put("valor", valor)
        })
    }

    fun salvarCombustivel(km: Double, kmPorLitro: Double, precoLitro: Double) {
        writableDatabase.insertWithOnConflict("combustivel", null, ContentValues().apply {
            put("data", hoje())
            put("km", km)
            put("km_por_litro", kmPorLitro)
            put("preco_litro", precoLitro)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun totalDeHoje(): Double =
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(valor), 0) FROM corridas WHERE data = ?", arrayOf(hoje())
        ).use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }

    fun resumoPorDia(): List<ResumoDia> =
        readableDatabase.rawQuery(
            """SELECT x.data, c.total, c.qtd, f.km, f.km_por_litro, f.preco_litro
               FROM (SELECT data FROM corridas UNION SELECT data FROM combustivel) x
               LEFT JOIN (SELECT data, SUM(valor) AS total, COUNT(*) AS qtd
                          FROM corridas GROUP BY data) c ON c.data = x.data
               LEFT JOIN combustivel f ON f.data = x.data
               ORDER BY x.data DESC""",
            null
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        ResumoDia(
                            data = LocalDate.parse(c.getString(0)),
                            bruto = c.doubleOuZero(1),
                            corridas = if (c.isNull(2)) 0 else c.getInt(2),
                            km = c.doubleOuZero(3),
                            kmPorLitro = c.doubleOuZero(4),
                            precoLitro = c.doubleOuZero(5)
                        )
                    )
                }
            }
        }

    fun apagarDia(data: LocalDate) {
        val arg = arrayOf(data.toString())
        writableDatabase.delete("corridas", "data = ?", arg)
        writableDatabase.delete("combustivel", "data = ?", arg)
    }

    private fun Cursor.doubleOuZero(coluna: Int) = if (isNull(coluna)) 0.0 else getDouble(coluna)

    companion object {
        fun hoje(): String = LocalDate.now().toString()
    }
}
