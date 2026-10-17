package com.calculadoraflutuante

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

// Entrega ao WhatsApp (ou outro app escolhido) a imagem do card gerada para compartilhar.
// Só serve o arquivo da pasta de compartilhamento, e só para quem recebeu a permissão no envio.
class ImagemProvider : ContentProvider() {

    override fun onCreate() = true

    private fun arquivo(uri: Uri): File {
        val pasta = pasta(context!!.cacheDir)
        val nome = uri.lastPathSegment ?: throw IllegalArgumentException("Sem arquivo")
        val f = File(pasta, nome)
        require(f.canonicalPath.startsWith(pasta.canonicalPath + File.separator)) { "Arquivo inválido" }
        return f
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(arquivo(uri), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun getType(uri: Uri) = "image/png"

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor {
        val f = arquivo(uri)
        val colunas = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(colunas).apply {
            addRow(colunas.map { if (it == OpenableColumns.SIZE) f.length() else f.name }.toTypedArray())
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        const val AUTORIDADE = "com.calculadoraflutuante.imagens"
        fun pasta(cache: File) = File(cache, "compartilhar").apply { mkdirs() }
        fun uri(nome: String): Uri = Uri.parse("content://$AUTORIDADE/$nome")
    }
}
