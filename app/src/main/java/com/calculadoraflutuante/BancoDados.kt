package com.calculadoraflutuante

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

// Um registro por dia: os valores das caixas do balão como estão no momento.
data class Dia(
    val data: LocalDate,
    val valor1: Double = 0.0,
    val valor2: Double = 0.0,
    val valor3: Double = 0.0,
    val km: Double = 0.0,
    val kmPorLitro: Double = 0.0,
    val precoLitro: Double = 0.0
) {
    val bruto: Double get() = valor1 + valor2 + valor3
    val gasto: Double get() = if (kmPorLitro > 0) km / kmPorLitro * precoLitro else 0.0
    val liquido: Double get() = bruto - gasto
}

class BancoDados(context: Context) : SQLiteOpenHelper(context, "calculadora.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        criarTabelaDias(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Versão 1 guardava cada corrida separada; junta tudo num registro por dia.
            criarTabelaDias(db)
            db.execSQL(
                """INSERT INTO dias (data, valor1, km, km_por_litro, preco_litro)
                   SELECT x.data, COALESCE(c.total, 0), COALESCE(f.km, 0),
                          COALESCE(f.km_por_litro, 0), COALESCE(f.preco_litro, 0)
                   FROM (SELECT data FROM corridas UNION SELECT data FROM combustivel) x
                   LEFT JOIN (SELECT data, SUM(valor) AS total FROM corridas GROUP BY data) c
                          ON c.data = x.data
                   LEFT JOIN combustivel f ON f.data = x.data"""
            )
            db.execSQL("DROP TABLE corridas")
            db.execSQL("DROP TABLE combustivel")
        }
    }

    private fun criarTabelaDias(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE dias (
                data TEXT PRIMARY KEY,
                valor1 REAL NOT NULL DEFAULT 0,
                valor2 REAL NOT NULL DEFAULT 0,
                valor3 REAL NOT NULL DEFAULT 0,
                km REAL NOT NULL DEFAULT 0,
                km_por_litro REAL NOT NULL DEFAULT 0,
                preco_litro REAL NOT NULL DEFAULT 0
            )"""
        )
    }

    // Grava o dia, substituindo o registro se ele já existir.
    fun salvar(dia: Dia) {
        writableDatabase.insertWithOnConflict("dias", null, ContentValues().apply {
            put("data", dia.data.toString())
            put("valor1", dia.valor1)
            put("valor2", dia.valor2)
            put("valor3", dia.valor3)
            put("km", dia.km)
            put("km_por_litro", dia.kmPorLitro)
            put("preco_litro", dia.precoLitro)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun buscar(data: LocalDate): Dia? =
        consultar("SELECT * FROM dias WHERE data = ?", arrayOf(data.toString())).firstOrNull()

    fun ultimoDia(): Dia? =
        consultar("SELECT * FROM dias ORDER BY data DESC LIMIT 1", null).firstOrNull()

    fun todosOsDias(): List<Dia> = consultar("SELECT * FROM dias ORDER BY data DESC", null)

    fun apagarDia(data: LocalDate) {
        writableDatabase.delete("dias", "data = ?", arrayOf(data.toString()))
    }

    private fun consultar(sql: String, args: Array<String>?): List<Dia> =
        readableDatabase.rawQuery(sql, args).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Dia(
                            data = LocalDate.parse(c.getString(c.getColumnIndexOrThrow("data"))),
                            valor1 = c.double("valor1"),
                            valor2 = c.double("valor2"),
                            valor3 = c.double("valor3"),
                            km = c.double("km"),
                            kmPorLitro = c.double("km_por_litro"),
                            precoLitro = c.double("preco_litro")
                        )
                    )
                }
            }
        }

    private fun Cursor.double(coluna: String) = getDouble(getColumnIndexOrThrow(coluna))
}
